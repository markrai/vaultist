package api

import (
	"encoding/json"
	"net/http"
)

type errorEnvelope struct {
	Error apiError `json:"error"`
}

type apiError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Details any    `json:"details,omitempty"`
}

func writeError(writer http.ResponseWriter, status int, code, message string, details any) {
	if capture, ok := writer.(*responseCapture); ok {
		capture.setErrorCode(code)
	}
	writeJSON(writer, status, errorEnvelope{Error: apiError{Code: code, Message: message, Details: details}})
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	writer.Header().Set("Content-Type", "application/json; charset=utf-8")
	writer.WriteHeader(status)
	_ = json.NewEncoder(writer).Encode(value)
}

func quoteETag(revision string) string { return `"` + revision + `"` }

func orEmpty[T any](values []T) []T {
	if values == nil {
		return []T{}
	}
	return values
}
