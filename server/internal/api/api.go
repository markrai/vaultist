package api

import (
	"log/slog"
	"net/http"
	"strings"

	"github.com/markrai/vaultist/server/internal/index"
)

const apiPrefix = "/api/v1"
const maxNoteWriteBytes = 32 << 20

type Authorizer interface {
	AuthorizeRead(*http.Request) bool
	AuthorizeRefresh(*http.Request) bool
	AuthorizeWrite(*http.Request) bool
}

type TailnetAuthorizer struct{}

func (TailnetAuthorizer) AuthorizeRead(*http.Request) bool    { return true }
func (TailnetAuthorizer) AuthorizeRefresh(*http.Request) bool { return true }
func (TailnetAuthorizer) AuthorizeWrite(*http.Request) bool   { return true }

type Handler struct {
	manager    *index.Manager
	authorizer Authorizer
	log        *slog.Logger
}

func NewHandler(manager *index.Manager, authorizer Authorizer) http.Handler {
	if authorizer == nil {
		authorizer = TailnetAuthorizer{}
	}
	return &Handler{manager: manager, authorizer: authorizer}
}

func (h *Handler) serve(writer http.ResponseWriter, request *http.Request) {
	writer.Header().Set("X-Content-Type-Options", "nosniff")
	writer.Header().Set("Cache-Control", "private, max-age=0, must-revalidate")
	if !strings.HasPrefix(request.URL.Path, apiPrefix) {
		writeError(writer, http.StatusNotFound, "route_not_found", "API route not found", nil)
		return
	}
	if request.Method == http.MethodPost && request.URL.Path == apiPrefix+"/notes" {
		if !h.authorizer.AuthorizeWrite(request) {
			writeError(writer, http.StatusForbidden, "forbidden", "Write access is not authorized", nil)
			return
		}
		h.createNoteRoute(writer, request)
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
	if request.Method == http.MethodPut && strings.HasPrefix(request.URL.Path, apiPrefix+"/notes/") {
		if !h.authorizer.AuthorizeWrite(request) {
			writeError(writer, http.StatusForbidden, "forbidden", "Write access is not authorized", nil)
			return
		}
		h.updateNoteRoute(writer, request)
		return
	}
	if request.Method == http.MethodDelete && strings.HasPrefix(request.URL.Path, apiPrefix+"/notes/") {
		if !h.authorizer.AuthorizeWrite(request) {
			writeError(writer, http.StatusForbidden, "forbidden", "Write access is not authorized", nil)
			return
		}
		h.deleteNoteRoute(writer, request)
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
