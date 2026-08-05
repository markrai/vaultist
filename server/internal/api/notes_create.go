package api

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
)

func (h *Handler) createNoteRoute(writer http.ResponseWriter, request *http.Request) {
	var body struct {
		ID      string `json:"id"`
		Content string `json:"content"`
	}
	if err := json.NewDecoder(io.LimitReader(request.Body, maxNoteWriteBytes+1024)).Decode(&body); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_note_body", "Note body must be valid JSON", nil)
		return
	}
	id := strings.TrimSpace(body.ID)
	if id == "" {
		writeError(writer, http.StatusBadRequest, "invalid_note_id", "Note ID is invalid", nil)
		return
	}
	content := []byte(body.Content)
	if len(content) > maxNoteWriteBytes {
		writeError(writer, http.StatusBadRequest, "invalid_note_body", "Note body exceeds the maximum size", nil)
		return
	}

	note, err := h.manager.CreateNote(request.Context(), id, content)
	if errors.Is(err, index.ErrInvalidNoteID) {
		writeError(writer, http.StatusBadRequest, "invalid_note_id", "Note ID is invalid", nil)
		return
	}
	if errors.Is(err, index.ErrNoteExists) {
		writeError(writer, http.StatusConflict, "note_exists", "A note with this ID already exists", nil)
		return
	}
	if err != nil {
		if errors.Is(err, index.ErrNotReady) {
			h.requireIndex(writer)
			return
		}
		if errors.Is(err, index.ErrWritePermission) {
			writeError(writer, http.StatusForbidden, "note_create_failed", "The server cannot write to the vault directory. Redeploy with deploy.sh so the container runs as the vault folder owner.", nil)
			return
		}
		if strings.Contains(err.Error(), "exceeds write limit") || strings.Contains(err.Error(), "too large") {
			writeError(writer, http.StatusBadRequest, "invalid_note_body", "Note body exceeds the maximum size", nil)
			return
		}
		writeError(writer, http.StatusInternalServerError, "note_create_failed", "Note could not be created", nil)
		return
	}

	writer.Header().Set("ETag", quoteETag(note.Revision))
	writeJSON(writer, http.StatusCreated, map[string]any{
		"id": note.ID, "path": note.Path, "filename": note.Filename, "title": note.Title,
		"aliases": orEmpty(note.Aliases), "headings": orEmpty(note.Headings), "links": orEmpty(note.Links),
		"attachments": orEmpty(note.Attachments), "modifiedAt": note.ModifiedAt, "size": note.Size,
		"revision": note.Revision, "content": body.Content, "error": note.Error,
	})
}
