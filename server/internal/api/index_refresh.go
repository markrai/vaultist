package api

import (
	"context"
	"errors"
	"net/http"

	"github.com/markrai/vaultist/server/internal/index"
)

func (h *Handler) refresh(writer http.ResponseWriter, _ *http.Request) {
	if err := h.manager.StartRefresh(context.Background()); errors.Is(err, index.ErrRefreshActive) {
		writeError(writer, http.StatusConflict, "index_refresh_running", "An index refresh is already running", nil)
		return
	} else if err != nil {
		writeError(writer, http.StatusInternalServerError, "index_refresh_failed", "Index refresh could not be started", nil)
		return
	}
	writeJSON(writer, http.StatusAccepted, map[string]string{"status": "indexing"})
}
