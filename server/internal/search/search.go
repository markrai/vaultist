package search

import (
	"context"
	"errors"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
)

type Mode string

const (
	ModeFiles   Mode = "files"
	ModeContent Mode = "content"
)

const cancelCheckInterval = 32

var ErrInvalidMode = errors.New("invalid search mode")

type Request struct {
	Query string
	Mode  Mode
}

type Hit struct {
	ID    string
	Name  string
	Title string
	Path  string
	Error string
	Rank  int
}

type Response struct {
	Hits  []Hit
	Query string
}

func Search(ctx context.Context, snapshot *index.Snapshot, req Request) (Response, error) {
	folded := strings.ToLower(req.Query)
	var matches []Hit
	for rank, id := range snapshot.OrderedNoteIDs {
		if rank%cancelCheckInterval == 0 {
			if err := ctx.Err(); err != nil {
				return Response{}, err
			}
		}
		note := snapshot.Notes[id]
		matched := false
		blobs, indexed := snapshot.SearchBlobs[id]
		switch req.Mode {
		case ModeFiles:
			if indexed {
				matched = strings.Contains(blobs.Files, folded)
			}
		case ModeContent:
			if indexed {
				matched = strings.Contains(blobs.Content, folded)
			}
		default:
			return Response{}, ErrInvalidMode
		}
		if matched {
			matches = append(matches, Hit{
				ID: note.ID, Name: note.Filename, Title: note.Title,
				Path: note.Path, Error: note.Error, Rank: rank,
			})
		}
	}
	return Response{Hits: matches, Query: req.Query}, nil
}
