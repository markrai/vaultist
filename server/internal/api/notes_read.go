package api

import (
	"io"
	"net/http"
	"path"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
)

func (h *Handler) noteRoute(writer http.ResponseWriter, request *http.Request) {
	raw := strings.TrimPrefix(request.URL.Path, apiPrefix+"/notes/")
	backlinks := strings.HasSuffix(raw, "/backlinks")
	if backlinks {
		raw = strings.TrimSuffix(raw, "/backlinks")
	}
	id, err := decodeID(raw)
	if err != nil || strings.EqualFold(path.Ext(id), ".md") {
		writeError(writer, http.StatusBadRequest, "invalid_note_id", "Note ID is invalid", nil)
		return
	}
	snapshot, ok := h.requireIndex(writer)
	if !ok {
		return
	}
	note, exists := snapshot.Notes[id]
	if !exists {
		writeError(writer, http.StatusNotFound, "note_not_found", "Note was not found", nil)
		return
	}
	if backlinks {
		writeJSON(writer, http.StatusOK, map[string]any{"noteId": id, "items": orEmpty(snapshot.Backlinks[id])})
		return
	}
	_, file, openErr := snapshot.OpenNote(id)
	if openErr != nil {
		writeError(writer, http.StatusInternalServerError, "note_read_failed", "Note content could not be read", nil)
		return
	}
	defer file.Close()
	content, readErr := io.ReadAll(io.LimitReader(file, 32<<20+1))
	if readErr != nil || len(content) > 32<<20 {
		writeError(writer, http.StatusInternalServerError, "note_read_failed", "Note content could not be read", nil)
		return
	}
	revision := index.ContentRevision(content)
	if request.Header.Get("If-None-Match") == quoteETag(revision) {
		writer.WriteHeader(http.StatusNotModified)
		return
	}
	writer.Header().Set("ETag", quoteETag(revision))
	writeJSON(writer, http.StatusOK, map[string]any{
		"id": note.ID, "path": note.Path, "filename": note.Filename, "title": note.Title,
		"aliases": orEmpty(note.Aliases), "headings": orEmpty(note.Headings), "links": orEmpty(note.Links),
		"attachments": orEmpty(note.Attachments), "modifiedAt": note.ModifiedAt, "size": note.Size,
		"revision": revision, "content": string(content), "error": note.Error,
	})
}
