package index

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path"
	"regexp"
	"strings"

	"github.com/markrai/vaultist/server/internal/model"
	"github.com/markrai/vaultist/server/internal/vault"
)

const maxNoteWriteBytes = 32 << 20

var revisionPattern = regexp.MustCompile(`^sha256:[0-9a-f]{64}$`)

var ErrInvalidRevision = errors.New("invalid revision")
var ErrWritePermission = errors.New("note write permission denied")
var ErrNoteExists = errors.New("note already exists")
var ErrInvalidNoteID = errors.New("invalid note id")
var ErrFolderExists = errors.New("folder already exists")
var ErrFolderNotEmpty = errors.New("folder not empty")
var ErrInvalidFolder = errors.New("invalid folder")

type RevisionConflictError struct {
	Expected string
	Actual   string
}

func (e *RevisionConflictError) Error() string {
	return fmt.Sprintf("revision conflict: expected %s actual %s", e.Expected, e.Actual)
}

func ContentRevision(content []byte) string {
	hash := sha256.Sum256(content)
	return "sha256:" + hex.EncodeToString(hash[:])
}

func normalizeRevisionETag(value string) (string, error) {
	value = strings.TrimSpace(value)
	value = strings.Trim(value, `"`)
	if !revisionPattern.MatchString(value) {
		return "", ErrInvalidRevision
	}
	return value, nil
}

func (m *Manager) WriteNoteContent(ctx context.Context, id, ifMatch string, content []byte) (*model.Note, error) {
	expectedRevision, err := normalizeRevisionETag(ifMatch)
	if err != nil {
		return nil, ErrInvalidRevision
	}
	if len(content) > maxNoteWriteBytes {
		return nil, fmt.Errorf("note body exceeds write limit")
	}
	m.mutationMu.Lock()
	defer m.mutationMu.Unlock()

	snapshot, err := m.Current()
	if err != nil {
		return nil, err
	}
	noteID, relativePath, err := resolveOnDiskNote(snapshot, m.root, id)
	if err != nil {
		return nil, err
	}

	file, err := vault.OpenFileInside(m.root, relativePath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("invalid note path")
	}
	current, err := readMutationNote(file)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("note could not be read")
	}
	actualRevision := ContentRevision(current)
	if actualRevision != expectedRevision {
		return nil, &RevisionConflictError{Expected: expectedRevision, Actual: actualRevision}
	}

	if err := vault.ReplaceFileAtomically(m.root, relativePath, content); err != nil {
		if isWritePermissionError(err) {
			return nil, ErrWritePermission
		}
		return nil, fmt.Errorf("note write failed: %w", err)
	}

	info, err := vault.StatInside(m.root, relativePath)
	if err != nil {
		return nil, fmt.Errorf("note write failed: %w", err)
	}

	note := &model.Note{
		ID: noteID, Path: relativePath, Filename: path.Base(relativePath),
		ModifiedAt: info.ModTime().UTC(), Size: info.Size(),
		Revision: ContentRevision(content),
	}
	parsed := m.parser.Parse(content, strings.TrimSuffix(path.Base(relativePath), path.Ext(relativePath)))
	note.Title = parsed.Title
	note.Aliases = parsed.Aliases
	note.Headings = parsed.Headings
	note.Links = parsed.Links
	note.Attachments = parsed.Attachments
	resolveLinksForResponse(snapshot, note)

	_ = m.StartRefresh(ctx)
	return note, nil
}

func (m *Manager) CreateNote(ctx context.Context, id string, content []byte) (*model.Note, error) {
	if len(content) > maxNoteWriteBytes {
		return nil, fmt.Errorf("note body exceeds write limit")
	}

	noteID, err := vault.NoteID(id + ".md")
	if err != nil {
		return nil, ErrInvalidNoteID
	}
	relativePath := noteID + ".md"
	m.mutationMu.Lock()
	defer m.mutationMu.Unlock()

	snapshot, err := m.Current()
	if err != nil {
		return nil, err
	}
	if _, ok := snapshot.Notes[noteID]; ok {
		return nil, ErrNoteExists
	}

	if _, statErr := vault.StatInside(m.root, relativePath); statErr == nil {
		return nil, ErrNoteExists
	} else if !os.IsNotExist(statErr) {
		return nil, fmt.Errorf("note create failed: %w", statErr)
	}

	if err := vault.CreateFileAtomically(m.root, relativePath, content); err != nil {
		if errors.Is(err, vault.ErrPathOccupied) || os.IsExist(err) {
			return nil, ErrNoteExists
		}
		if isWritePermissionError(err) {
			return nil, ErrWritePermission
		}
		return nil, fmt.Errorf("note create failed: %w", err)
	}

	info, err := vault.StatInside(m.root, relativePath)
	if err != nil {
		return nil, fmt.Errorf("note create failed: %w", err)
	}

	note := &model.Note{
		ID: noteID, Path: relativePath, Filename: path.Base(relativePath),
		ModifiedAt: info.ModTime().UTC(), Size: info.Size(),
		Revision: ContentRevision(content),
	}
	parsed := m.parser.Parse(content, strings.TrimSuffix(path.Base(relativePath), path.Ext(relativePath)))
	note.Title = parsed.Title
	note.Aliases = parsed.Aliases
	note.Headings = parsed.Headings
	note.Links = parsed.Links
	note.Attachments = parsed.Attachments
	resolveLinksForResponse(snapshot, note)

	_ = m.StartRefresh(ctx)
	return note, nil
}

func (m *Manager) CreateFolder(ctx context.Context, folderPath string) (string, string, error) {
	normalized, err := vault.NormalizeRelative(folderPath)
	if err != nil {
		return "", "", ErrInvalidFolder
	}
	if vault.IsHidden(normalized) {
		return "", "", ErrInvalidFolder
	}
	if strings.HasSuffix(strings.ToLower(normalized), ".md") {
		return "", "", ErrInvalidFolder
	}
	m.mutationMu.Lock()
	defer m.mutationMu.Unlock()

	snapshot, err := m.Current()
	if err != nil {
		return "", "", err
	}
	if _, ok := snapshot.Folders[normalized]; ok {
		return "", "", ErrFolderExists
	}
	if _, ok := snapshot.Notes[normalized]; ok {
		return "", "", ErrFolderExists
	}

	if info, statErr := vault.StatInside(m.root, normalized); statErr == nil {
		if info.IsDir() {
			return "", "", ErrFolderExists
		}
		return "", "", ErrFolderExists
	} else if !os.IsNotExist(statErr) {
		return "", "", fmt.Errorf("folder create failed: %w", statErr)
	}

	noteRelativePath := normalized + ".md"
	if _, noteStatErr := vault.StatInside(m.root, noteRelativePath); noteStatErr == nil {
		return "", "", ErrFolderExists
	} else if !os.IsNotExist(noteStatErr) {
		return "", "", fmt.Errorf("folder create failed: %w", noteStatErr)
	}

	if err := vault.MkdirInside(m.root, normalized); err != nil {
		if errors.Is(err, vault.ErrFolderExists) {
			return "", "", ErrFolderExists
		}
		if errors.Is(err, vault.ErrPathOccupied) {
			return "", "", ErrFolderExists
		}
		if isWritePermissionError(err) {
			return "", "", ErrWritePermission
		}
		return "", "", fmt.Errorf("folder create failed: %w", err)
	}

	name := path.Base(normalized)
	_ = m.StartRefresh(context.Background())
	return name, normalized, nil
}

func (m *Manager) DeleteFolder(ctx context.Context, folderPath string) error {
	normalized, err := vault.NormalizeRelative(folderPath)
	if err != nil {
		return ErrInvalidFolder
	}
	if vault.IsHidden(normalized) {
		return ErrInvalidFolder
	}
	if strings.HasSuffix(strings.ToLower(normalized), ".md") {
		return ErrInvalidFolder
	}
	m.mutationMu.Lock()
	defer m.mutationMu.Unlock()

	if info, statErr := vault.StatInside(m.root, normalized); statErr != nil {
		if os.IsNotExist(statErr) {
			return ErrNotFound
		}
		return fmt.Errorf("folder delete failed: %w", statErr)
	} else if !info.IsDir() {
		return ErrInvalidFolder
	}

	if err := vault.RemoveDirInside(m.root, normalized); err != nil {
		if errors.Is(err, vault.ErrFolderNotEmpty) {
			return ErrFolderNotEmpty
		}
		if errors.Is(err, vault.ErrPathOccupied) {
			return ErrInvalidFolder
		}
		if errors.Is(err, os.ErrNotExist) {
			return ErrNotFound
		}
		if isWritePermissionError(err) {
			return ErrWritePermission
		}
		return fmt.Errorf("folder delete failed: %w", err)
	}

	_ = m.StartRefresh(ctx)
	return nil
}

func (m *Manager) DeleteNote(ctx context.Context, id, ifMatch string) error {
	expectedRevision, err := normalizeRevisionETag(ifMatch)
	if err != nil {
		return ErrInvalidRevision
	}
	m.mutationMu.Lock()
	defer m.mutationMu.Unlock()

	snapshot, err := m.Current()
	if err != nil {
		return err
	}
	_, relativePath, err := resolveOnDiskNote(snapshot, m.root, id)
	if err != nil {
		return err
	}

	file, err := vault.OpenFileInside(m.root, relativePath)
	if err != nil {
		if os.IsNotExist(err) {
			return ErrNotFound
		}
		return fmt.Errorf("invalid note path")
	}
	current, err := readMutationNote(file)
	if err != nil {
		if os.IsNotExist(err) {
			return ErrNotFound
		}
		return fmt.Errorf("note could not be read")
	}
	actualRevision := ContentRevision(current)
	if actualRevision != expectedRevision {
		return &RevisionConflictError{Expected: expectedRevision, Actual: actualRevision}
	}

	if err := vault.DeleteFileInside(m.root, relativePath); err != nil {
		if isWritePermissionError(err) {
			return ErrWritePermission
		}
		return fmt.Errorf("note delete failed: %w", err)
	}

	_ = m.StartRefresh(ctx)
	return nil
}

// resolveOnDiskNote finds a writable note by snapshot entry or, if the index
// has not caught up yet, by validating the id and confirming the .md file exists.
func resolveOnDiskNote(snapshot *Snapshot, root, id string) (noteID, relativePath string, err error) {
	if existing, ok := snapshot.Notes[id]; ok {
		return existing.ID, existing.Path, nil
	}
	noteID, err = vault.NoteID(id + ".md")
	if err != nil {
		return "", "", ErrInvalidNoteID
	}
	relativePath = noteID + ".md"
	if _, statErr := vault.StatInside(root, relativePath); statErr != nil {
		if os.IsNotExist(statErr) {
			return "", "", ErrNotFound
		}
		return "", "", fmt.Errorf("note could not be read: %w", statErr)
	}
	return noteID, relativePath, nil
}

func resolveLinksForResponse(snapshot *Snapshot, note *model.Note) {
	for linkIndex := range note.Links {
		link := &note.Links[linkIndex]
		if link.IsAsset {
			link.Resolution = snapshot.ResolveAsset(note.Path, link.Target)
		} else {
			link.Resolution = snapshot.ResolveNote(note.Path, link.Target, link.Kind == model.LinkMarkdown)
		}
	}
}

func isWritePermissionError(err error) bool {
	for err != nil {
		if errors.Is(err, os.ErrPermission) {
			return true
		}
		if strings.Contains(strings.ToLower(err.Error()), "permission denied") {
			return true
		}
		err = errors.Unwrap(err)
	}
	return false
}

func readMutationNote(file *os.File) ([]byte, error) {
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, maxNoteWriteBytes+1))
	if err != nil || len(content) > maxNoteWriteBytes {
		return nil, fmt.Errorf("note could not be read")
	}
	return content, nil
}
