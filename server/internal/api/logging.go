package api

import (
	"log/slog"
	"net/http"
	"strings"
	"time"
)

type responseCapture struct {
	http.ResponseWriter
	status    int
	errorCode string
}

func (capture *responseCapture) WriteHeader(status int) {
	if capture.status == 0 {
		capture.status = status
	}
	capture.ResponseWriter.WriteHeader(status)
}

func (capture *responseCapture) Write(body []byte) (int, error) {
	if capture.status == 0 {
		capture.status = http.StatusOK
	}
	return capture.ResponseWriter.Write(body)
}

func (capture *responseCapture) setErrorCode(code string) {
	capture.errorCode = code
}

func routePattern(path string) string {
	switch {
	case path == apiPrefix+"/status":
		return apiPrefix + "/status"
	case path == apiPrefix+"/vault":
		return apiPrefix + "/vault"
	case path == apiPrefix+"/notes":
		return apiPrefix + "/notes"
	case path == apiPrefix+"/search":
		return apiPrefix + "/search"
	case path == apiPrefix+"/index/status":
		return apiPrefix + "/index/status"
	case path == apiPrefix+"/index/refresh":
		return apiPrefix + "/index/refresh"
	case strings.HasPrefix(path, apiPrefix+"/notes/") && strings.HasSuffix(path, "/backlinks"):
		return apiPrefix + "/notes/{id}/backlinks"
	case strings.HasPrefix(path, apiPrefix+"/notes/"):
		return apiPrefix + "/notes/{id}"
	case strings.HasPrefix(path, apiPrefix+"/assets/"):
		return apiPrefix + "/assets/{id}"
	case strings.HasPrefix(path, apiPrefix+"/"):
		return apiPrefix + "/{unknown}"
	default:
		return path
	}
}

func (h *Handler) ServeHTTP(writer http.ResponseWriter, request *http.Request) {
	start := time.Now()
	capture := &responseCapture{ResponseWriter: writer}
	h.serve(capture, request)
	status := capture.status
	if status == 0 {
		status = http.StatusOK
	}
	attrs := []any{
		"method", request.Method,
		"route", routePattern(request.URL.Path),
		"status", status,
		"duration_ms", time.Since(start).Milliseconds(),
	}
	if capture.errorCode != "" {
		attrs = append(attrs, "error_code", capture.errorCode)
	}
	h.logger().Info("request", attrs...)
}

func (h *Handler) logger() *slog.Logger {
	if h.log != nil {
		return h.log
	}
	return slog.Default()
}
