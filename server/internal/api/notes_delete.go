package api

import (
	"errors"
	"net/http"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
)

func (h *Handler) deleteNoteRoute(writer http.ResponseWriter, request *http.Request) {
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

	err = h.manager.DeleteNote(request.Context(), id, ifMatch)
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
			writeError(writer, http.StatusForbidden, "note_delete_failed", "The server cannot write to the vault directory. Redeploy with deploy.sh so the container runs as the vault folder owner.", nil)
			return
		}
		writeError(writer, http.StatusInternalServerError, "note_delete_failed", "Note could not be deleted", nil)
		return
	}

	writer.WriteHeader(http.StatusNoContent)
}
