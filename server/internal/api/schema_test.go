package api

import (
	"encoding/json"
	"net/http"
	"path/filepath"
	"runtime"
	"testing"

	"github.com/getkin/kin-openapi/openapi3"
)

func openAPIPath(t *testing.T) string {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("runtime.Caller failed")
	}
	return filepath.Clean(filepath.Join(filepath.Dir(file), "..", "..", "..", "api", "openapi.yaml"))
}

func loadOpenAPI(t *testing.T) *openapi3.T {
	t.Helper()
	loader := &openapi3.Loader{IsExternalRefsAllowed: true}
	document, err := loader.LoadFromFile(openAPIPath(t))
	if err != nil {
		t.Fatalf("load OpenAPI: %v", err)
	}
	if err := document.Validate(loader.Context); err != nil {
		t.Fatalf("validate OpenAPI document: %v", err)
	}
	return document
}

func validateSchema(t *testing.T, document *openapi3.T, schemaName string, body []byte) {
	t.Helper()
	schemaRef := document.Components.Schemas[schemaName]
	if schemaRef == nil {
		t.Fatalf("schema %q not found", schemaName)
	}
	var value any
	if err := json.Unmarshal(body, &value); err != nil {
		t.Fatalf("unmarshal %s body: %v", schemaName, err)
	}
	if err := schemaRef.Value.VisitJSON(value); err != nil {
		t.Fatalf("schema %s: %v\nbody=%s", schemaName, err, body)
	}
}

func TestResponsesMatchOpenAPISchemas(t *testing.T) {
	document := loadOpenAPI(t)
	server := contractFixture(t)
	defer server.Close()
	base := server.URL

	validateSchema(t, document, "StatusResponse", mustGETBody(t, base+"/api/v1/status"))
	validateSchema(t, document, "VaultResponse", mustGETBody(t, base+"/api/v1/vault"))
	validateSchema(t, document, "BrowseResponse", mustGETBody(t, base+"/api/v1/notes?limit=1"))
	validateSchema(t, document, "SearchResponse", mustGETBody(t, base+"/api/v1/search?q=other&limit=10"))
	validateSchema(t, document, "SearchResponse", mustGETBody(t, base+"/api/v1/search?q=Folder%2FNote&mode=content&limit=10"))
	validateSchema(t, document, "NoteResponse", mustGETBody(t, base+"/api/v1/notes/Folder/Note"))
	validateSchema(t, document, "BacklinksResponse", mustGETBody(t, base+"/api/v1/notes/Home/backlinks"))

	errorBody, status := fetchResponse(t, http.MethodGet, base+"/api/v1/search?q=test&mode=invalid", nil)
	if status != http.StatusBadRequest {
		t.Fatalf("invalid search mode status = %d body=%s", status, errorBody)
	}
	validateSchema(t, document, "ErrorResponse", errorBody)
}
