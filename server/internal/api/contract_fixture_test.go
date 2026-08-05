package api

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/markrai/vaultist/server/internal/index"
)

func contractFixture(t *testing.T) *httptest.Server {
	_, server := contractFixtureWithManager(t)
	return server
}

func contractFixtureWithManager(t *testing.T) (*index.Manager, *httptest.Server) {
	t.Helper()
	root := t.TempDir()
	writeFile(t, root, "Home.md", "# Home\n[[Folder/Note]]\n![[pixel.png]]")
	writeFile(t, root, "Folder/Note.md", "# Note\n[[Home]]")
	writeFile(t, root, "Folder/Other.md", "# Other")
	writeFile(t, root, "Percent%20.md", "# Literal percent")
	writeFile(t, root, "pixel.png", "0123456789")
	manager, err := index.NewManager(root, "Contract Vault")
	if err != nil {
		t.Fatal(err)
	}
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	return manager, httptest.NewServer(NewHandler(manager, nil))
}

// holdActiveRefresh starts a refresh and blocks it until the returned release
// function is called. Use this when tests must observe the indexing state.
func holdActiveRefresh(t *testing.T, manager *index.Manager) func() {
	t.Helper()
	entered := make(chan struct{})
	done := make(chan struct{})
	manager.SetRefreshHook(func() {
		close(entered)
		<-done
	})
	if err := manager.StartRefresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	select {
	case <-entered:
	case <-time.After(2 * time.Second):
		t.Fatal("timed out waiting for refresh to start")
	}
	return func() {
		close(done)
		manager.SetRefreshHook(nil)
	}
}

func fetchResponse(t *testing.T, method, endpoint string, body io.Reader) ([]byte, int) {
	t.Helper()
	request, err := http.NewRequest(method, endpoint, body)
	if err != nil {
		t.Fatal(err)
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

func mustGETBody(t *testing.T, endpoint string) []byte {
	t.Helper()
	body, status := fetchResponse(t, http.MethodGet, endpoint, nil)
	if status != http.StatusOK {
		t.Fatalf("GET %s: status %d body=%s", endpoint, status, body)
	}
	return body
}

func writeFile(t *testing.T, root, relative, content string) {
	t.Helper()
	filePath := filepath.Join(root, filepath.FromSlash(relative))
	if err := os.MkdirAll(filepath.Dir(filePath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filePath, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}
