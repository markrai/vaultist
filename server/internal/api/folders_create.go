package api

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
)

func (h *Handler) createFolderRoute(writer http.ResponseWriter, request *http.Request) {
	var body struct {
		Path string `json:"path"`
	}
	if err := json.NewDecoder(io.LimitReader(request.Body, 4096)).Decode(&body); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_folder", "Folder path must be valid JSON", nil)
		return
	}
	folderPath := strings.TrimSpace(body.Path)
	if folderPath == "" {
		writeError(writer, http.StatusBadRequest, "invalid_folder", "Folder path is invalid", nil)
		return
	}

	name, path, err := h.manager.CreateFolder(request.Context(), folderPath)
	if errors.Is(err, index.ErrInvalidFolder) {
		writeError(writer, http.StatusBadRequest, "invalid_folder", "Folder path is invalid", nil)
		return
	}
	if errors.Is(err, index.ErrFolderExists) {
		writeError(writer, http.StatusConflict, "folder_exists", "A folder with this path already exists", nil)
		return
	}
	if err != nil {
		if errors.Is(err, index.ErrNotReady) {
			h.requireIndex(writer)
			return
		}
		if errors.Is(err, index.ErrWritePermission) {
			writeError(writer, http.StatusForbidden, "folder_create_failed", "The server cannot write to the vault directory. Redeploy with deploy.sh so the container runs as the vault folder owner.", nil)
			return
		}
		writeError(writer, http.StatusInternalServerError, "folder_create_failed", "Folder could not be created", nil)
		return
	}

	writeJSON(writer, http.StatusCreated, browseItem{
		Kind: "folder",
		Name: name,
		Path: path,
	})
}
