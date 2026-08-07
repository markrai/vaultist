package index

import (
	"fmt"
	"io"
	"os"
	"path"
	"strings"

	"github.com/markrai/vaultist/server/internal/model"
	"github.com/markrai/vaultist/server/internal/vault"
)

const maxNoteReadBytes = 32 << 20

// GetNoteForRead returns note metadata and body bytes for GET /notes/{id}.
// Indexed notes use snapshot metadata; notes written before the index catches up
// are served from on-disk content when the .md file exists.
func (m *Manager) GetNoteForRead(id string) (*model.Note, []byte, error) {
	snapshot, err := m.Current()
	if err != nil {
		return nil, nil, err
	}
	noteID, relativePath, err := resolveOnDiskNote(snapshot, m.root, id)
	if err != nil {
		return nil, nil, err
	}
	filePath, err := vault.JoinInside(m.root, relativePath)
	if err != nil {
		return nil, nil, ErrNotFound
	}
	content, err := readNoteFile(filePath)
	if err != nil {
		return nil, nil, err
	}
	if indexed, ok := snapshot.Notes[noteID]; ok {
		note := *indexed
		note.Revision = ContentRevision(content)
		return &note, content, nil
	}
	info, err := os.Stat(filePath)
	if err != nil {
		return nil, nil, fmt.Errorf("note could not be read")
	}
	note, err := m.noteFromDisk(noteID, relativePath, content, info)
	if err != nil {
		return nil, nil, err
	}
	resolveLinksForResponse(snapshot, note)
	return note, content, nil
}

func readNoteFile(filePath string) ([]byte, error) {
	file, err := os.Open(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, ErrNotFound
		}
		return nil, fmt.Errorf("note could not be read")
	}
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, maxNoteReadBytes+1))
	if err != nil || len(content) > maxNoteReadBytes {
		return nil, fmt.Errorf("note could not be read")
	}
	return content, nil
}

func (m *Manager) noteFromDisk(noteID, relativePath string, content []byte, info os.FileInfo) (*model.Note, error) {
	title := strings.TrimSuffix(path.Base(relativePath), path.Ext(relativePath))
	note := &model.Note{
		ID: noteID, Path: relativePath, Filename: path.Base(relativePath),
		Title: title, ModifiedAt: info.ModTime().UTC(), Size: info.Size(),
		Revision: ContentRevision(content),
	}
	parsed := m.parser.Parse(content, title)
	note.Title = parsed.Title
	note.Aliases = parsed.Aliases
	note.Headings = parsed.Headings
	note.Links = parsed.Links
	note.Attachments = parsed.Attachments
	return note, nil
}
