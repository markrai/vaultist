package api

import (
	"errors"
	"net/http"
	"net/url"
	"path"
	"strconv"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
	"github.com/markrai/vaultist/server/internal/vault"
)

func (h *Handler) requireIndex(writer http.ResponseWriter) (*index.Snapshot, bool) {
	snapshot, err := h.manager.Current()
	if err == nil {
		return snapshot, true
	}
	state := h.manager.State()
	if state.State == "unavailable" {
		writeError(writer, http.StatusServiceUnavailable, "vault_unavailable", "The vault is currently unavailable", nil)
	} else {
		writeError(writer, http.StatusServiceUnavailable, "index_not_ready", "The vault index is not ready", nil)
	}
	return nil, false
}

func decodeID(raw string) (string, error) {
	decoded := strings.Trim(raw, "/")
	if decoded == "" {
		return "", vault.ErrInvalidPath
	}
	return vault.NormalizeRelative(decoded)
}

func pagination(values url.Values) (limit, cursor int, valid bool) {
	limit = 50
	if raw := values.Get("limit"); raw != "" {
		parsed, err := strconv.Atoi(raw)
		if err != nil || parsed < 1 || parsed > 200 {
			return 0, 0, false
		}
		limit = parsed
	}
	if raw := values.Get("cursor"); raw != "" {
		parsed, err := strconv.Atoi(raw)
		if err != nil || parsed < 0 {
			return 0, 0, false
		}
		cursor = parsed
	}
	return limit, cursor, true
}

func paginate[T any](items []T, cursor, limit int) ([]T, string) {
	if cursor >= len(items) {
		return []T{}, ""
	}
	end := cursor + limit
	if end >= len(items) {
		return items[cursor:], ""
	}
	return items[cursor:end], strconv.Itoa(end)
}

func parseNoteIDFromPath(requestPath string) (string, error) {
	raw := strings.TrimPrefix(requestPath, apiPrefix+"/notes/")
	if strings.HasSuffix(raw, "/backlinks") {
		return "", errors.New("invalid note route")
	}
	id, err := decodeID(raw)
	if err != nil || strings.EqualFold(path.Ext(id), ".md") {
		return "", errors.New("invalid note id")
	}
	return id, nil
}
