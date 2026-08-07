package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestCreateFolderSuccess(t *testing.T) {
	_, server := contractFixtureWithManager(t)
	defer server.Close()
	body := `{"path":"Projects/Ideas"}`
	payload, status := fetchWithHeaders(t, http.MethodPost, server.URL+"/api/v1/folders", strings.NewReader(body), map[string]string{
		"Content-Type": "application/json",
	})
	if status != http.StatusCreated {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var response map[string]any
	if err := json.Unmarshal(payload, &response); err != nil {
		t.Fatal(err)
	}
	if response["kind"] != "folder" {
		t.Fatalf("kind = %#v", response["kind"])
	}
	if response["name"] != "Ideas" || response["path"] != "Projects/Ideas" {
		t.Fatalf("response = %#v", response)
	}
}

func TestCreateFolderConflict(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()
	body := `{"path":"Folder"}`
	payload, status := fetchWithHeaders(t, http.MethodPost, server.URL+"/api/v1/folders", strings.NewReader(body), map[string]string{
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
	if errorObj["code"] != "folder_exists" {
		t.Fatalf("code = %#v", errorObj["code"])
	}
}

func TestCreateFolderInvalidPath(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()
	body := `{"path":"../secret"}`
	payload, status := fetchWithHeaders(t, http.MethodPost, server.URL+"/api/v1/folders", strings.NewReader(body), map[string]string{
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
	if errorObj["code"] != "invalid_folder" {
		t.Fatalf("code = %#v", errorObj["code"])
	}
}

func TestCreateFolderWriteForbidden(t *testing.T) {
	manager, _ := contractFixtureWithManager(t)
	denied := httptest.NewServer(NewHandler(manager, denyAuthorizer{}))
	defer denied.Close()
	body := `{"path":"Blocked"}`
	_, status := fetchWithHeaders(t, http.MethodPost, denied.URL+"/api/v1/folders", strings.NewReader(body), map[string]string{
		"Content-Type": "application/json",
	})
	if status != http.StatusForbidden {
		t.Fatalf("status = %d", status)
	}
}

func TestBrowseIncludesEmptyIndexedFolder(t *testing.T) {
	_, server := contractFixtureWithManager(t)
	defer server.Close()
	payload, status := fetchWithHeaders(t, http.MethodPost, server.URL+"/api/v1/folders", strings.NewReader(`{"path":"Empty Projects"}`), map[string]string{
		"Content-Type": "application/json",
	})
	if status != http.StatusCreated {
		t.Fatalf("create folder status = %d body=%s", status, payload)
	}
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		rootBrowse := getJSON(t, server.URL+"/api/v1/notes")
		items := rootBrowse["items"].([]any)
		for _, item := range items {
			entry := item.(map[string]any)
			if entry["kind"] == "folder" && entry["path"] == "Empty Projects" {
				return
			}
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatal("timed out waiting for empty folder in browse")
}
