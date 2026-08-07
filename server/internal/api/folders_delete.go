package api

import (
	"errors"
	"net/http"

	"github.com/markrai/vaultist/server/internal/index"
)

func (h *Handler) deleteFolderRoute(writer http.ResponseWriter, request *http.Request) {
	folderPath, err := parseFolderPathFromPath(request.URL.Path)
	if err != nil {
		writeError(writer, http.StatusBadRequest, "invalid_folder", "Folder path is invalid", nil)
		return
	}

	err = h.manager.DeleteFolder(request.Context(), folderPath)
	if errors.Is(err, index.ErrInvalidFolder) {
		writeError(writer, http.StatusBadRequest, "invalid_folder", "Folder path is invalid", nil)
		return
	}
	if errors.Is(err, index.ErrNotFound) {
		writeError(writer, http.StatusNotFound, "folder_not_found", "Folder was not found", nil)
		return
	}
	if errors.Is(err, index.ErrFolderNotEmpty) {
		writeError(writer, http.StatusConflict, "folder_not_empty", "The folder is not empty", nil)
		return
	}
	if err != nil {
		if errors.Is(err, index.ErrNotReady) {
			h.requireIndex(writer)
			return
		}
		if errors.Is(err, index.ErrWritePermission) {
			writeError(writer, http.StatusForbidden, "folder_delete_failed", "The server cannot write to the vault directory. Redeploy with deploy.sh so the container runs as the vault folder owner.", nil)
			return
		}
		writeError(writer, http.StatusInternalServerError, "folder_delete_failed", "Folder could not be deleted", nil)
		return
	}

	writer.WriteHeader(http.StatusNoContent)
}
