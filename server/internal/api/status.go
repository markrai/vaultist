package api

import "net/http"

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
		"indexedAt": snapshot.BuiltAt, "readOnly": false,
	})
}
