package api

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
)

func (h *Handler) updateNoteRoute(writer http.ResponseWriter, request *http.Request) {
	id, err := parseNoteIDFromPath(request.URL.Path)
	if err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_note_id", "Note ID is invalid", nil)
		return
	}
	ifMatch := request.Header.Get("If-Match")
	if strings.TrimSpace(ifMatch) == "" {
		writeError(writer, http.StatusBadRequest, "invalid_revision", "If-Match header is required", nil)
		return
	}
	var body struct {
		Content string `json:"content"`
	}
	if err := json.NewDecoder(io.LimitReader(request.Body, maxNoteWriteBytes+1024)).Decode(&body); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_note_body", "Note body must be valid JSON", nil)
		return
	}
	content := []byte(body.Content)
	if len(content) > maxNoteWriteBytes {
		writeError(writer, http.StatusBadRequest, "invalid_note_body", "Note body exceeds the maximum size", nil)
		return
	}

	note, err := h.manager.WriteNoteContent(request.Context(), id, ifMatch, content)
	if errors.Is(err, index.ErrNotFound) {
		writeError(writer, http.StatusNotFound, "note_not_found", "Note was not found", nil)
		return
	}
	if errors.Is(err, index.ErrInvalidRevision) {
		writeError(writer, http.StatusBadRequest, "invalid_revision", "If-Match revision is invalid", nil)
		return
	}
	var conflict *index.RevisionConflictError
	if errors.As(err, &conflict) {
		writeError(writer, http.StatusConflict, "revision_conflict", "The note changed since it was loaded", map[string]string{
			"expected": conflict.Expected,
			"actual":   conflict.Actual,
		})
		return
	}
	if err != nil {
		if errors.Is(err, index.ErrNotReady) {
			h.requireIndex(writer)
			return
		}
		if errors.Is(err, index.ErrWritePermission) {
			writeError(writer, http.StatusForbidden, "note_write_failed", "The server cannot write to the vault directory. Redeploy with deploy.sh so the container runs as the vault folder owner.", nil)
			return
		}
		if strings.Contains(err.Error(), "exceeds write limit") || strings.Contains(err.Error(), "too large") {
			writeError(writer, http.StatusBadRequest, "invalid_note_body", "Note body exceeds the maximum size", nil)
			return
		}
		writeError(writer, http.StatusInternalServerError, "note_write_failed", "Note could not be saved", nil)
		return
	}

	writer.Header().Set("ETag", quoteETag(note.Revision))
	writeJSON(writer, http.StatusOK, map[string]any{
		"id": note.ID, "path": note.Path, "filename": note.Filename, "title": note.Title,
		"aliases": orEmpty(note.Aliases), "headings": orEmpty(note.Headings), "links": orEmpty(note.Links),
		"attachments": orEmpty(note.Attachments), "modifiedAt": note.ModifiedAt, "size": note.Size,
		"revision": note.Revision, "content": body.Content, "error": note.Error,
	})
}
