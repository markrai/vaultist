package search

import (
	"errors"
	"io"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
	"github.com/markrai/vaultist/server/internal/model"
)

type Mode string

const (
	ModeFiles   Mode = "files"
	ModeContent Mode = "content"
)

var ErrInvalidMode = errors.New("invalid search mode")

type Hit struct {
	ID    string
	Name  string
	Title string
	Path  string
	Error string
}

const maxSearchNoteBytes = 32 << 20

func Search(snapshot *index.Snapshot, query string, mode Mode) ([]Hit, error) {
	folded := strings.ToLower(query)
	var matches []Hit
	for _, id := range snapshot.OrderedNoteIDs {
		note := snapshot.Notes[id]
		matched := false
		switch mode {
		case ModeFiles:
			matched = matchFiles(note, folded)
		case ModeContent:
			matched = matchContent(snapshot, note, folded)
		default:
			return nil, ErrInvalidMode
		}
		if matched {
			matches = append(matches, Hit{
				ID: note.ID, Name: note.Filename, Title: note.Title,
				Path: note.Path, Error: note.Error,
			})
		}
	}
	return matches, nil
}

func matchFiles(note *model.Note, foldedQuery string) bool {
	if strings.Contains(strings.ToLower(note.Filename), foldedQuery) ||
		strings.Contains(strings.ToLower(note.Title), foldedQuery) {
		return true
	}
	for _, alias := range note.Aliases {
		if strings.Contains(strings.ToLower(alias), foldedQuery) {
			return true
		}
	}
	return false
}

func matchContent(snapshot *index.Snapshot, note *model.Note, foldedQuery string) bool {
	_, file, err := snapshot.OpenNote(note.ID)
	if err != nil {
		return false
	}
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, maxSearchNoteBytes+1))
	if err != nil || len(content) > maxSearchNoteBytes {
		return false
	}
	return strings.Contains(strings.ToLower(string(content)), foldedQuery)
}
