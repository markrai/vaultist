package api

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestUpdateNoteSuccess(t *testing.T) {
	manager, server := contractFixtureWithManager(t)
	defer server.Close()
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	body := `{"content":"# Note\nupdated body"}`
	payload, status := fetchWithHeaders(t, http.MethodPut, server.URL+"/api/v1/notes/Folder/Note", strings.NewReader(body), map[string]string{
		"If-Match":     quoteETag(note.Revision),
		"Content-Type": "application/json",
	})
	if status != http.StatusOK {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var response map[string]any
	if err := json.Unmarshal(payload, &response); err != nil {
		t.Fatal(err)
	}
	if response["content"] != "# Note\nupdated body" {
		t.Fatalf("content = %#v", response["content"])
	}
	if response["revision"] == note.Revision {
		t.Fatal("expected revision to change")
	}
}

func TestUpdateNoteRevisionConflict(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()
	body := `{"content":"# Note\nupdated body"}`
	payload, status := fetchWithHeaders(t, http.MethodPut, server.URL+"/api/v1/notes/Folder/Note", strings.NewReader(body), map[string]string{
		"If-Match":     `"sha256:0000000000000000000000000000000000000000000000000000000000000000"`,
		"Content-Type": "application/json",
	})
	if status != http.StatusConflict {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var decoded map[string]any
	if err := json.Unmarshal(payload, &decoded); err != nil {
		t.Fatal(err)
	}
	errorObj := decoded["error"].(map[string]any)
	if errorObj["code"] != "revision_conflict" {
		t.Fatalf("code = %#v", errorObj["code"])
	}
}

func TestUpdateNoteMissingIfMatch(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()
	body := `{"content":"# Note\nupdated body"}`
	payload, status := fetchWithHeaders(t, http.MethodPut, server.URL+"/api/v1/notes/Folder/Note", strings.NewReader(body), map[string]string{
		"Content-Type": "application/json",
	})
	if status != http.StatusBadRequest {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var decoded map[string]any
	if err := json.Unmarshal(payload, &decoded); err != nil {
		t.Fatal(err)
	}
	errorObj := decoded["error"].(map[string]any)
	if errorObj["code"] != "invalid_revision" {
		t.Fatalf("code = %#v", errorObj["code"])
	}
}

func TestUpdateNoteWriteForbidden(t *testing.T) {
	manager, _ := contractFixtureWithManager(t)
	denied := httptest.NewServer(NewHandler(manager, denyAuthorizer{}))
	defer denied.Close()
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	body := `{"content":"# Note\nupdated body"}`
	_, status := fetchWithHeaders(t, http.MethodPut, denied.URL+"/api/v1/notes/Folder/Note", strings.NewReader(body), map[string]string{
		"If-Match":     quoteETag(note.Revision),
		"Content-Type": "application/json",
	})
	if status != http.StatusForbidden {
		t.Fatalf("status = %d", status)
	}
}

func TestDeleteNoteSuccess(t *testing.T) {
	manager, server := contractFixtureWithManager(t)
	defer server.Close()
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	payload, status := fetchWithHeaders(t, http.MethodDelete, server.URL+"/api/v1/notes/Folder/Note", nil, map[string]string{
		"If-Match": quoteETag(note.Revision),
	})
	if status != http.StatusNoContent {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	if len(payload) != 0 {
		t.Fatalf("expected empty body, got %q", payload)
	}
}

func TestDeleteNoteRevisionConflict(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()
	payload, status := fetchWithHeaders(t, http.MethodDelete, server.URL+"/api/v1/notes/Folder/Note", nil, map[string]string{
		"If-Match": `"sha256:0000000000000000000000000000000000000000000000000000000000000000"`,
	})
	if status != http.StatusConflict {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var decoded map[string]any
	if err := json.Unmarshal(payload, &decoded); err != nil {
		t.Fatal(err)
	}
	errorObj := decoded["error"].(map[string]any)
	if errorObj["code"] != "revision_conflict" {
		t.Fatalf("code = %#v", errorObj["code"])
	}
}

func TestDeleteNoteMissingIfMatch(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()
	payload, status := fetchWithHeaders(t, http.MethodDelete, server.URL+"/api/v1/notes/Folder/Note", nil, nil)
	if status != http.StatusBadRequest {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var decoded map[string]any
	if err := json.Unmarshal(payload, &decoded); err != nil {
		t.Fatal(err)
	}
	errorObj := decoded["error"].(map[string]any)
	if errorObj["code"] != "invalid_revision" {
		t.Fatalf("code = %#v", errorObj["code"])
	}
}

func TestDeleteNoteWriteForbidden(t *testing.T) {
	manager, _ := contractFixtureWithManager(t)
	denied := httptest.NewServer(NewHandler(manager, denyAuthorizer{}))
	defer denied.Close()
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	_, status := fetchWithHeaders(t, http.MethodDelete, denied.URL+"/api/v1/notes/Folder/Note", nil, map[string]string{
		"If-Match": quoteETag(note.Revision),
	})
	if status != http.StatusForbidden {
		t.Fatalf("status = %d", status)
	}
}

func TestCreateNoteSuccess(t *testing.T) {
	_, server := contractFixtureWithManager(t)
	defer server.Close()
	body := `{"id":"Folder/New Note","content":"# New Note\n\n"}`
	payload, status := fetchWithHeaders(t, http.MethodPost, server.URL+"/api/v1/notes", strings.NewReader(body), map[string]string{
		"Content-Type": "application/json",
	})
	if status != http.StatusCreated {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var response map[string]any
	if err := json.Unmarshal(payload, &response); err != nil {
		t.Fatal(err)
	}
	if response["id"] != "Folder/New Note" {
		t.Fatalf("id = %#v", response["id"])
	}
	if response["content"] != "# New Note\n\n" {
		t.Fatalf("content = %#v", response["content"])
	}
}

func TestCreateNoteConflict(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()
	body := `{"id":"Folder/Note","content":"# duplicate"}`
	payload, status := fetchWithHeaders(t, http.MethodPost, server.URL+"/api/v1/notes", strings.NewReader(body), map[string]string{
		"Content-Type": "application/json",
	})
	if status != http.StatusConflict {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var decoded map[string]any
	if err := json.Unmarshal(payload, &decoded); err != nil {
		t.Fatal(err)
	}
	errorObj := decoded["error"].(map[string]any)
	if errorObj["code"] != "note_exists" {
		t.Fatalf("code = %#v", errorObj["code"])
	}
}

func TestCreateNoteInvalidID(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()
	body := `{"id":"../secret","content":""}`
	payload, status := fetchWithHeaders(t, http.MethodPost, server.URL+"/api/v1/notes", strings.NewReader(body), map[string]string{
		"Content-Type": "application/json",
	})
	if status != http.StatusBadRequest {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var decoded map[string]any
	if err := json.Unmarshal(payload, &decoded); err != nil {
		t.Fatal(err)
	}
	errorObj := decoded["error"].(map[string]any)
	if errorObj["code"] != "invalid_note_id" {
		t.Fatalf("code = %#v", errorObj["code"])
	}
}

func TestCreateNoteWriteForbidden(t *testing.T) {
	manager, _ := contractFixtureWithManager(t)
	denied := httptest.NewServer(NewHandler(manager, denyAuthorizer{}))
	defer denied.Close()
	body := `{"id":"Folder/Blocked","content":""}`
	_, status := fetchWithHeaders(t, http.MethodPost, denied.URL+"/api/v1/notes", strings.NewReader(body), map[string]string{
		"Content-Type": "application/json",
	})
	if status != http.StatusForbidden {
		t.Fatalf("status = %d", status)
	}
}

func fetchWithHeaders(t *testing.T, method, endpoint string, body io.Reader, headers map[string]string) ([]byte, int) {
	t.Helper()
	request, err := http.NewRequest(method, endpoint, body)
	if err != nil {
		t.Fatal(err)
	}
	for key, value := range headers {
		request.Header.Set(key, value)
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	payload, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err)
	}
	return payload, response.StatusCode
}
