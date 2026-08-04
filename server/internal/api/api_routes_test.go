package api

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

type denyAuthorizer struct{}

func (denyAuthorizer) AuthorizeRead(*http.Request) bool    { return false }
func (denyAuthorizer) AuthorizeRefresh(*http.Request) bool { return false }

func TestBrowseFolderAndPagination(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()

	folder := getJSON(t, server.URL+"/api/v1/notes?folder=Folder")
	if folder["folder"] != "Folder" {
		t.Fatalf("folder = %#v", folder["folder"])
	}
	items := folder["items"].([]any)
	if len(items) != 2 {
		t.Fatalf("folder items = %d, want 2 notes", len(items))
	}

	first := getJSON(t, server.URL+"/api/v1/notes?limit=1")
	cursor, _ := first["nextCursor"].(string)
	if cursor == "" {
		t.Fatal("expected nextCursor on first page")
	}
	second := getJSON(t, server.URL+"/api/v1/notes?limit=1&cursor="+cursor)
	if len(second["items"].([]any)) == 0 {
		t.Fatal("expected items on second page")
	}

	assertAPIError(t, server.URL+"/api/v1/notes?cursor=bad", http.StatusBadRequest, "invalid_pagination")
}

func TestSearchDefaultModeAndEmptyQuery(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()

	files := getJSON(t, server.URL+"/api/v1/search?q=other&mode=files&limit=10")
	if len(files["items"].([]any)) != 1 {
		t.Fatalf("files search = %#v", files)
	}
	defaultMode := getJSON(t, server.URL+"/api/v1/search?q=other&limit=10")
	if len(defaultMode["items"].([]any)) != 1 {
		t.Fatalf("default search = %#v", defaultMode)
	}
	assertAPIError(t, server.URL+"/api/v1/search?q=&limit=10", http.StatusBadRequest, "invalid_query")
}

func TestIndexStatus(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()

	body := getJSON(t, server.URL+"/api/v1/index/status")
	if body["state"] != "ready" {
		t.Fatalf("index state = %#v", body["state"])
	}
}

func TestRefreshConflictWhileIndexing(t *testing.T) {
	manager, server := contractFixtureWithManager(t)
	defer server.Close()

	if err := manager.StartRefresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	request, _ := http.NewRequest(http.MethodPost, server.URL+"/api/v1/index/refresh", strings.NewReader(""))
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	data, _ := io.ReadAll(response.Body)
	if response.StatusCode != http.StatusConflict || !strings.Contains(string(data), "index_refresh_running") {
		t.Fatalf("refresh conflict = %d %s", response.StatusCode, data)
	}
}

func TestAssetFullGetAndNotFound(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()

	response, err := http.Get(server.URL + "/api/v1/assets/pixel.png")
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(response.Body)
	response.Body.Close()
	if response.StatusCode != http.StatusOK || string(body) != "0123456789" {
		t.Fatalf("asset GET = %d %q", response.StatusCode, body)
	}
	if ct := response.Header.Get("Content-Type"); ct == "" {
		t.Fatal("missing Content-Type")
	}

	assertAPIError(t, server.URL+"/api/v1/assets/missing.png", http.StatusNotFound, "asset_not_found")
}

func TestBacklinksNotFoundAndEmpty(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()

	assertAPIError(t, server.URL+"/api/v1/notes/Nope/backlinks", http.StatusNotFound, "note_not_found")

	empty := getJSON(t, server.URL+"/api/v1/notes/Folder/Other/backlinks")
	items, ok := empty["items"].([]any)
	if !ok {
		t.Fatalf("items = %#v", empty["items"])
	}
	if len(items) != 0 {
		t.Fatalf("expected empty backlinks, got %d", len(items))
	}
}

func TestRouteNotFoundAndForbidden(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()

	assertAPIError(t, server.URL+"/api/v1/nope", http.StatusNotFound, "route_not_found")

	manager, _ := contractFixtureWithManager(t)
	denied := httptest.NewServer(NewHandler(manager, denyAuthorizer{}))
	defer denied.Close()

	readResponse, err := http.Get(denied.URL + "/api/v1/vault")
	if err != nil {
		t.Fatal(err)
	}
	readBody, _ := io.ReadAll(readResponse.Body)
	readResponse.Body.Close()
	if readResponse.StatusCode != http.StatusForbidden || !strings.Contains(string(readBody), "forbidden") {
		t.Fatalf("read forbidden = %d %s", readResponse.StatusCode, readBody)
	}

	refreshRequest, _ := http.NewRequest(http.MethodPost, denied.URL+"/api/v1/index/refresh", strings.NewReader(""))
	refreshResponse, err := http.DefaultClient.Do(refreshRequest)
	if err != nil {
		t.Fatal(err)
	}
	refreshBody, _ := io.ReadAll(refreshResponse.Body)
	refreshResponse.Body.Close()
	if refreshResponse.StatusCode != http.StatusForbidden || !strings.Contains(string(refreshBody), "forbidden") {
		t.Fatalf("refresh forbidden = %d %s", refreshResponse.StatusCode, refreshBody)
	}
}
