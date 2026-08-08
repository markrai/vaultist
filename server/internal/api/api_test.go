package api

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"

	"github.com/markrai/vaultist/server/internal/index"
)

func TestHTTPContract(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()

	assertStatus(t, http.MethodGet, server.URL+"/api/v1/status", nil, http.StatusOK)
	assertStatus(t, http.MethodGet, server.URL+"/api/v1/vault", nil, http.StatusOK)

	list := getJSON(t, server.URL+"/api/v1/notes?limit=1")
	if list["nextCursor"] == "" {
		t.Fatalf("pagination = %#v", list)
	}
	search := getJSON(t, server.URL+"/api/v1/search?q=other&limit=10")
	if len(search["items"].([]any)) != 1 {
		t.Fatalf("search = %#v", search)
	}
	contentSearch := getJSON(t, server.URL+"/api/v1/search?q=Folder%2FNote&mode=content&limit=10")
	if len(contentSearch["items"].([]any)) != 1 {
		t.Fatalf("content search = %#v", contentSearch)
	}
	contentMiss := getJSON(t, server.URL+"/api/v1/search?q=not-in-body&mode=content&limit=10")
	if len(contentMiss["items"].([]any)) != 0 {
		t.Fatalf("content miss = %#v", contentMiss)
	}
	assertAPIError(t, server.URL+"/api/v1/search?q=test&mode=invalid", http.StatusBadRequest, "invalid_query")

	response, err := http.Get(server.URL + "/api/v1/notes/Folder/Note")
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		t.Fatalf("note status = %d", response.StatusCode)
	}
	var noteBody map[string]any
	if err := json.NewDecoder(response.Body).Decode(&noteBody); err != nil {
		t.Fatal(err)
	}
	for _, key := range []string{"aliases", "headings", "links", "attachments"} {
		if _, ok := noteBody[key].([]any); !ok {
			t.Fatalf("%s must be a JSON array, got %#v", key, noteBody[key])
		}
	}

	emptyNoteURL := server.URL + "/api/v1/notes/Folder/Other"
	emptyNote := getJSON(t, emptyNoteURL)
	for _, key := range []string{"aliases", "headings", "links", "attachments"} {
		values, ok := emptyNote[key].([]any)
		if !ok {
			t.Fatalf("empty note %s must be a JSON array, got %#v", key, emptyNote[key])
		}
		if values == nil {
			t.Fatalf("empty note %s must not be null", key)
		}
	}
	encodedResponse, err := http.Get(server.URL + "/api/v1/notes/Folder%2FNote")
	if err != nil {
		t.Fatal(err)
	}
	encodedResponse.Body.Close()
	if encodedResponse.StatusCode != http.StatusOK {
		t.Fatalf("encoded note status = %d", encodedResponse.StatusCode)
	}
	percentResponse, err := http.Get(server.URL + "/api/v1/notes/Percent%2520")
	if err != nil {
		t.Fatal(err)
	}
	percentResponse.Body.Close()
	if percentResponse.StatusCode != http.StatusOK {
		t.Fatalf("percent note status = %d", percentResponse.StatusCode)
	}
	etag := response.Header.Get("ETag")
	if etag == "" {
		t.Fatal("missing ETag")
	}
	request, _ := http.NewRequest(http.MethodGet, server.URL+"/api/v1/notes/Folder/Note", nil)
	request.Header.Set("If-None-Match", etag)
	conditional, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	conditional.Body.Close()
	if conditional.StatusCode != http.StatusNotModified {
		t.Fatalf("conditional = %d", conditional.StatusCode)
	}

	backlinks := getJSON(t, server.URL+"/api/v1/notes/Home/backlinks")
	if len(backlinks["items"].([]any)) != 1 {
		t.Fatalf("backlinks = %#v", backlinks)
	}

	assetRequest, _ := http.NewRequest(http.MethodGet, server.URL+"/api/v1/assets/pixel.png", nil)
	assetRequest.Header.Set("Range", "bytes=2-5")
	assetResponse, err := http.DefaultClient.Do(assetRequest)
	if err != nil {
		t.Fatal(err)
	}
	assetBody, _ := io.ReadAll(assetResponse.Body)
	assetResponse.Body.Close()
	if assetResponse.StatusCode != http.StatusPartialContent || string(assetBody) != "2345" {
		t.Fatalf("asset %d %q", assetResponse.StatusCode, assetBody)
	}

	assertAPIError(t, server.URL+"/api/v1/notes/Nope", http.StatusNotFound, "note_not_found")
	assertAPIError(t, server.URL+"/api/v1/notes/../secret", http.StatusBadRequest, "invalid_note_id")
	assertAPIError(t, server.URL+"/api/v1/notes?folder=.trash", http.StatusBadRequest, "invalid_folder")
	assertAPIError(t, server.URL+"/api/v1/assets/../secret.png", http.StatusBadRequest, "invalid_asset_id")
	assertStatus(t, http.MethodPost, server.URL+"/api/v1/index/refresh", strings.NewReader(""), http.StatusAccepted)
}

func TestIndexUnavailable(t *testing.T) {
	manager, _ := index.NewManager(filepath.Join(t.TempDir(), "missing"), "Missing")
	recorder := httptest.NewRecorder()
	NewHandler(manager, nil).ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/v1/vault", nil))
	if recorder.Code != http.StatusServiceUnavailable || !strings.Contains(recorder.Body.String(), "index_not_ready") {
		t.Fatalf("response = %d %s", recorder.Code, recorder.Body.String())
	}
}

func assertStatus(t *testing.T, method, endpoint string, body io.Reader, status int) {
	t.Helper()
	request, _ := http.NewRequest(method, endpoint, body)
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != status {
		data, _ := io.ReadAll(response.Body)
		t.Fatalf("%s: got %d body=%s", endpoint, response.StatusCode, data)
	}
}

func getJSON(t *testing.T, endpoint string) map[string]any {
	t.Helper()
	response, err := http.Get(endpoint)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	var value map[string]any
	if err := json.NewDecoder(response.Body).Decode(&value); err != nil {
		t.Fatal(err)
	}
	return value
}

func assertAPIError(t *testing.T, endpoint string, status int, code string) {
	t.Helper()
	response, err := http.Get(endpoint)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	data, _ := io.ReadAll(response.Body)
	if response.StatusCode != status || !strings.Contains(string(data), code) {
		t.Fatalf("error = %d %s", response.StatusCode, data)
	}
}
