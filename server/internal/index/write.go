package index

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
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

type RevisionConflictError struct {
	Expected string
	Actual   string
}

func (e *RevisionConflictError) Error() string {
	return fmt.Sprintf("revision conflict: expected %s actual %s", e.Expected, e.Actual)
}

func contentRevision(content []byte) string {
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

	snapshot, err := m.Current()
	if err != nil {
		return nil, err
	}
	existing, ok := snapshot.Notes[id]
	if !ok {
		return nil, ErrNotFound
	}

	filePath, err := vault.JoinInside(m.root, existing.Path)
	if err != nil {
		return nil, fmt.Errorf("invalid note path")
	}
	current, err := os.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("note could not be read")
	}
	actualRevision := contentRevision(current)
	if actualRevision != expectedRevision {
		return nil, &RevisionConflictError{Expected: expectedRevision, Actual: actualRevision}
	}

	if err := vault.ReplaceFileAtomically(m.root, existing.Path, content); err != nil {
		return nil, fmt.Errorf("note write failed: %w", err)
	}

	info, err := os.Stat(filePath)
	if err != nil {
		return nil, fmt.Errorf("note write failed: %w", err)
	}

	note := &model.Note{
		ID: existing.ID, Path: existing.Path, Filename: existing.Filename,
		ModifiedAt: info.ModTime().UTC(), Size: info.Size(),
		Revision: contentRevision(content),
	}
	parsed := m.parser.Parse(content, strings.TrimSuffix(path.Base(existing.Path), path.Ext(existing.Path)))
	note.Title = parsed.Title
	note.Aliases = parsed.Aliases
	note.Headings = parsed.Headings
	note.Links = parsed.Links
	note.Attachments = parsed.Attachments
	resolveLinksForResponse(snapshot, note)

	_ = m.StartRefresh(ctx)
	return note, nil
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
