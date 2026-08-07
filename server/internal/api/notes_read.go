package api

import (
	"errors"
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
	if backlinks {
		items := snapshot.Backlinks[id]
		if len(items) == 0 {
			if _, _, err := h.manager.GetNoteForRead(id); errors.Is(err, index.ErrNotFound) {
				writeError(writer, http.StatusNotFound, "note_not_found", "Note was not found", nil)
				return
			}
		}
		writeJSON(writer, http.StatusOK, map[string]any{"noteId": id, "items": orEmpty(items)})
		return
	}
	note, content, err := h.manager.GetNoteForRead(id)
	if errors.Is(err, index.ErrNotFound) {
		writeError(writer, http.StatusNotFound, "note_not_found", "Note was not found", nil)
		return
	} else if err != nil {
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
