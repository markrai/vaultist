package api

import (
	"context"
	"net/http"
)

func (h *Handler) refresh(writer http.ResponseWriter, _ *http.Request) {
	if err := h.manager.StartRefresh(context.Background()); err != nil {
		writeError(writer, http.StatusInternalServerError, "index_refresh_failed", "Index refresh could not be started", nil)
		return
	}
	writeJSON(writer, http.StatusAccepted, map[string]string{"status": "indexing"})
}
