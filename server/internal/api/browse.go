package api

import (
	"net/http"
	"sort"
	"strings"
	"time"

	"github.com/markrai/vaultist/server/internal/index"
	"github.com/markrai/vaultist/server/internal/vault"
)

type browseItem struct {
	Kind       string     `json:"kind"`
	ID         string     `json:"id,omitempty"`
	Name       string     `json:"name"`
	Title      string     `json:"title,omitempty"`
	Path       string     `json:"path"`
	Error      string     `json:"error,omitempty"`
	ModifiedAt *time.Time `json:"modifiedAt,omitempty"`
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
		modifiedAt := note.ModifiedAt
		notes = append(notes, browseItem{
			Kind: "note", ID: note.ID, Name: note.Filename, Title: note.Title,
			Path: note.Path, Error: note.Error, ModifiedAt: &modifiedAt,
		})
	}
	mergeIndexedFolders(folders, snapshot, folder)
	items := make([]browseItem, 0, len(folders)+len(notes))
	for _, folderItem := range folders {
		items = append(items, folderItem)
	}
	sort.Slice(items, func(i, j int) bool { return strings.ToLower(items[i].Path) < strings.ToLower(items[j].Path) })
	items = append(items, notes...)
	return items
}

func mergeIndexedFolders(folders map[string]browseItem, snapshot *index.Snapshot, folder string) {
	for folderPath := range snapshot.Folders {
		if !isImmediateChildFolder(folder, folderPath) {
			continue
		}
		name := folderPath
		if slash := strings.LastIndexByte(folderPath, '/'); slash >= 0 {
			name = folderPath[slash+1:]
		}
		folders[folderPath] = browseItem{Kind: "folder", Name: name, Path: folderPath}
	}
}

func isImmediateChildFolder(parent, child string) bool {
	if child == parent {
		return false
	}
	if parent == "" {
		return !strings.Contains(child, "/")
	}
	prefix := parent + "/"
	if !strings.HasPrefix(child, prefix) {
		return false
	}
	remainder := strings.TrimPrefix(child, prefix)
	return !strings.Contains(remainder, "/")
}
