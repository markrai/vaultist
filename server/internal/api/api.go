package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/url"
	"path"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/markrai/vaultist/server/internal/index"
	"github.com/markrai/vaultist/server/internal/model"
	"github.com/markrai/vaultist/server/internal/vault"
)

const apiPrefix = "/api/v1"

type Authorizer interface {
	AuthorizeRead(*http.Request) bool
	AuthorizeRefresh(*http.Request) bool
}

type TailnetAuthorizer struct{}

func (TailnetAuthorizer) AuthorizeRead(*http.Request) bool    { return true }
func (TailnetAuthorizer) AuthorizeRefresh(*http.Request) bool { return true }

type Handler struct {
	manager    *index.Manager
	authorizer Authorizer
}

func NewHandler(manager *index.Manager, authorizer Authorizer) http.Handler {
	if authorizer == nil {
		authorizer = TailnetAuthorizer{}
	}
	return &Handler{manager: manager, authorizer: authorizer}
}

func (h *Handler) ServeHTTP(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("X-Content-Type-Options", "nosniff")
	writer.Header().Set("Cache-Control", "private, max-age=0, must-revalidate")
	if !strings.HasPrefix(request.URL.Path, apiPrefix) {
		writeError(writer, http.StatusNotFound, "route_not_found", "API route not found", nil)
		return
	}
	if request.Method == http.MethodPost && request.URL.Path == apiPrefix+"/index/refresh" {
		if !h.authorizer.AuthorizeRefresh(request) {
			writeError(writer, http.StatusForbidden, "forbidden", "Refresh is not authorized", nil)
			return
		}
		h.refresh(writer, request)
		return
	}
	if !h.authorizer.AuthorizeRead(request) {
		writeError(writer, http.StatusForbidden, "forbidden", "Read access is not authorized", nil)
		return
	}
	switch {
	case request.Method == http.MethodGet && request.URL.Path == apiPrefix+"/status":
		h.status(writer)
	case request.Method == http.MethodGet && request.URL.Path == apiPrefix+"/vault":
		h.vault(writer)
	case request.Method == http.MethodGet && request.URL.Path == apiPrefix+"/notes":
		h.notes(writer, request)
	case request.Method == http.MethodGet && strings.HasPrefix(request.URL.Path, apiPrefix+"/notes/"):
		h.noteRoute(writer, request)
	case request.Method == http.MethodGet && request.URL.Path == apiPrefix+"/search":
		h.search(writer, request)
	case request.Method == http.MethodGet && strings.HasPrefix(request.URL.Path, apiPrefix+"/assets/"):
		h.asset(writer, request)
	case request.Method == http.MethodGet && request.URL.Path == apiPrefix+"/index/status":
		writeJSON(writer, http.StatusOK, h.manager.State())
	default:
		writeError(writer, http.StatusNotFound, "route_not_found", "API route not found", nil)
	}
}

func (h *Handler) status(writer http.ResponseWriter) {
	state := h.manager.State()
	writeJSON(writer, http.StatusOK, map[string]any{
		"service": "vaultist", "version": "v1", "status": "ok", "index": state,
	})
}

func (h *Handler) vault(writer http.ResponseWriter) {
	snapshot, ok := h.requireIndex(writer)
	if !ok {
		return
	}
	writeJSON(writer, http.StatusOK, map[string]any{
		"name": snapshot.VaultName, "noteCount": len(snapshot.Notes),
		"assetCount": len(snapshot.Assets), "generation": snapshot.Generation,
		"indexedAt": snapshot.BuiltAt, "readOnly": true,
	})
}

type browseItem struct {
	Kind  string `json:"kind"`
	ID    string `json:"id,omitempty"`
	Name  string `json:"name"`
	Title string `json:"title,omitempty"`
	Path  string `json:"path"`
	Error string `json:"error,omitempty"`
}

func (h *Handler) notes(writer http.ResponseWriter, request *http.Request) {
	snapshot, ok := h.requireIndex(writer)
	if !ok {
		return
	}
	folder := strings.Trim(strings.ReplaceAll(request.URL.Query().Get("folder"), "\\", "/"), "/")
	if folder != "" {
		if _, err := vault.NormalizeRelative(folder); err != nil {
			writeError(writer, http.StatusBadRequest, "invalid_folder", "Folder is invalid", nil)
			return
		}
	}
	limit, cursor, valid := pagination(request.URL.Query())
	if !valid {
		writeError(writer, http.StatusBadRequest, "invalid_pagination", "Pagination parameters are invalid", nil)
		return
	}
	items := browseItems(snapshot, folder)
	pageItems, next := paginate(items, cursor, limit)
	writeJSON(writer, http.StatusOK, map[string]any{"items": pageItems, "nextCursor": next, "folder": folder})
}

func browseItems(snapshot *index.Snapshot, folder string) []browseItem {
	prefix := folder
	if prefix != "" {
		prefix += "/"
	}
	folders := map[string]browseItem{}
	var notes []browseItem
	for _, id := range snapshot.OrderedNoteIDs {
		note := snapshot.Notes[id]
		if !strings.HasPrefix(note.Path, prefix) {
			continue
		}
		remainder := strings.TrimPrefix(note.Path, prefix)
		if slash := strings.IndexByte(remainder, '/'); slash >= 0 {
			name := remainder[:slash]
			folderPath := prefix + name
			folders[folderPath] = browseItem{Kind: "folder", Name: name, Path: folderPath}
			continue
		}
		notes = append(notes, browseItem{Kind: "note", ID: note.ID, Name: note.Filename, Title: note.Title, Path: note.Path, Error: note.Error})
	}
	items := make([]browseItem, 0, len(folders)+len(notes))
	for _, folderItem := range folders {
		items = append(items, folderItem)
	}
	sort.Slice(items, func(i, j int) bool { return strings.ToLower(items[i].Path) < strings.ToLower(items[j].Path) })
	items = append(items, notes...)
	return items
}

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
	if request.Header.Get("If-None-Match") == quoteETag(note.Revision) {
		writer.WriteHeader(http.StatusNotModified)
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
	writer.Header().Set("ETag", quoteETag(note.Revision))
	writeJSON(writer, http.StatusOK, map[string]any{
		"id": note.ID, "path": note.Path, "filename": note.Filename, "title": note.Title,
		"aliases": orEmpty(note.Aliases), "headings": orEmpty(note.Headings), "links": orEmpty(note.Links),
		"attachments": orEmpty(note.Attachments), "modifiedAt": note.ModifiedAt, "size": note.Size,
		"revision": note.Revision, "content": string(content), "error": note.Error,
	})
}

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
	folded := strings.ToLower(query)
	var matches []browseItem
	for _, id := range snapshot.OrderedNoteIDs {
		note := snapshot.Notes[id]
		matched := false
		switch mode {
		case "files":
			matched = strings.Contains(strings.ToLower(note.Filename), folded) || strings.Contains(strings.ToLower(note.Title), folded)
			if !matched {
				for _, alias := range note.Aliases {
					if strings.Contains(strings.ToLower(alias), folded) {
						matched = true
						break
					}
				}
			}
		case "content":
			matched = noteContentContains(snapshot, note, folded)
		}
		if matched {
			matches = append(matches, browseItem{Kind: "note", ID: note.ID, Name: note.Filename, Title: note.Title, Path: note.Path, Error: note.Error})
		}
	}
	pageItems, next := paginate(matches, cursor, limit)
	writeJSON(writer, http.StatusOK, map[string]any{"items": pageItems, "nextCursor": next, "query": query})
}

const maxSearchNoteBytes = 32 << 20

func noteContentContains(snapshot *index.Snapshot, note *model.Note, foldedQuery string) bool {
	_, file, err := snapshot.OpenNote(note.ID)
	if err != nil {
		return false
	}
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, maxSearchNoteBytes+1))
	if err != nil || len(content) > maxSearchNoteBytes {
		return false
	}
	return strings.Contains(strings.ToLower(string(content)), foldedQuery)
}

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
	if request.Header.Get("If-None-Match") == asset.ETag {
		writer.WriteHeader(http.StatusNotModified)
		return
	}
	writer.Header().Set("Content-Type", asset.MediaType)
	writer.Header().Set("ETag", asset.ETag)
	writer.Header().Set("Cache-Control", "private, max-age=3600, must-revalidate")
	http.ServeContent(writer, request, asset.Filename, asset.ModifiedAt, file)
}

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

func quoteETag(revision string) string { return `"` + revision + `"` }

type errorEnvelope struct {
	Error apiError `json:"error"`
}
type apiError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Details any    `json:"details,omitempty"`
}

func writeError(writer http.ResponseWriter, status int, code, message string, details any) {
	writeJSON(writer, status, errorEnvelope{Error: apiError{Code: code, Message: message, Details: details}})
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	writer.Header().Set("Content-Type", "application/json; charset=utf-8")
	writer.WriteHeader(status)
	_ = json.NewEncoder(writer).Encode(value)
}

func orEmpty[T any](values []T) []T {
	if values == nil {
		return []T{}
	}
	return values
}

var _ = time.Time{}
var _ model.IndexState
