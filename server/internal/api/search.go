package api

import (
	"context"
	"errors"
	"net/http"
	"strings"

	"github.com/markrai/vaultist/server/internal/search"
)

func (h *Handler) search(writer http.ResponseWriter, request *http.Request) {
	snapshot, ok := h.requireIndex(writer)
	if !ok {
		return
	}
	query := strings.TrimSpace(request.URL.Query().Get("q"))
	if len([]rune(query)) < 1 || len([]rune(query)) > 200 {
		writeError(writer, http.StatusBadRequest, "invalid_query", "Search query must be between 1 and 200 characters", nil)
		return
	}
	mode := strings.ToLower(strings.TrimSpace(request.URL.Query().Get("mode")))
	if mode == "" {
		mode = "files"
	}
	if mode != "files" && mode != "content" {
		writeError(writer, http.StatusBadRequest, "invalid_query", "Search mode must be files or content", nil)
		return
	}
	limit, cursor, valid := pagination(request.URL.Query())
	if !valid {
		writeError(writer, http.StatusBadRequest, "invalid_pagination", "Pagination parameters are invalid", nil)
		return
	}
	hits, err := search.Search(request.Context(), snapshot, search.Request{Query: query, Mode: search.Mode(mode)})
	if err != nil {
		if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
			return
		}
		writeError(writer, http.StatusBadRequest, "invalid_query", "Search mode must be files or content", nil)
		return
	}
	matches := make([]browseItem, 0, len(hits.Hits))
	for _, hit := range hits.Hits {
		item := browseItem{
			Kind: "note", ID: hit.ID, Name: hit.Name, Title: hit.Title, Path: hit.Path, Error: hit.Error,
		}
		if note, ok := snapshot.Notes[hit.ID]; ok {
			modifiedAt := note.ModifiedAt
			item.ModifiedAt = &modifiedAt
		}
		matches = append(matches, item)
	}
	pageItems, next := paginate(matches, cursor, limit)
	writeJSON(writer, http.StatusOK, map[string]any{"items": pageItems, "nextCursor": next, "query": query})
}
