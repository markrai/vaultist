package api

import (
	"errors"
	"mime"
	"net/http"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
)

func (h *Handler) asset(writer http.ResponseWriter, request *http.Request) {
	raw := strings.TrimPrefix(request.URL.Path, apiPrefix+"/assets/")
	id, err := decodeID(raw)
	if err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_asset_id", "Asset ID is invalid", nil)
		return
	}
	snapshot, ok := h.requireIndex(writer)
	if !ok {
		return
	}
	asset, file, openErr := snapshot.OpenAsset(id)
	if errors.Is(openErr, index.ErrNotFound) {
		writeError(writer, http.StatusNotFound, "asset_not_found", "Asset was not found", nil)
		return
	}
	if openErr != nil {
		writeError(writer, http.StatusInternalServerError, "asset_read_failed", "Asset could not be read", nil)
		return
	}
	defer file.Close()
	writer.Header().Set("Content-Type", asset.MediaType)
	writer.Header().Set("ETag", asset.ETag)
	writer.Header().Set("Cache-Control", "private, max-age=3600, must-revalidate")
	if asset.MediaType == "image/svg+xml" {
		writer.Header().Set("Content-Disposition", mime.FormatMediaType("attachment", map[string]string{"filename": asset.Filename}))
		writer.Header().Set("Content-Security-Policy", "sandbox; default-src 'none'")
	}
	if request.Header.Get("If-None-Match") == asset.ETag {
		writer.WriteHeader(http.StatusNotModified)
		return
	}
	http.ServeContent(writer, request, asset.Filename, asset.ModifiedAt, file)
}
