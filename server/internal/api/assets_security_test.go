package api

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/markrai/vaultist/server/internal/index"
)

func TestSVGAssetPreservesBytesAndHardensBrowserNavigation(t *testing.T) {
	root := t.TempDir()
	svg := `<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script><image href="https://example.invalid/tracker.png"/></svg>`
	writeFile(t, root, "malicious.svg", svg)
	manager, err := index.NewManager(root, "SVG")
	if err != nil {
		t.Fatal(err)
	}
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(NewHandler(manager, nil))
	defer server.Close()

	response, err := http.Get(server.URL + "/api/v1/assets/malicious.svg")
	if err != nil {
		t.Fatal(err)
	}
	body, err := io.ReadAll(response.Body)
	response.Body.Close()
	if err != nil {
		t.Fatal(err)
	}
	if response.StatusCode != http.StatusOK || string(body) != svg {
		t.Fatalf("status=%d body=%q", response.StatusCode, body)
	}
	assertSVGSecurityHeaders(t, response, true)
	etag := response.Header.Get("ETag")

	rangeRequest, _ := http.NewRequest(http.MethodGet, server.URL+"/api/v1/assets/malicious.svg", nil)
	rangeRequest.Header.Set("Range", "bytes=0-9")
	rangeResponse, err := http.DefaultClient.Do(rangeRequest)
	if err != nil {
		t.Fatal(err)
	}
	rangeResponse.Body.Close()
	if rangeResponse.StatusCode != http.StatusPartialContent {
		t.Fatalf("range status = %d", rangeResponse.StatusCode)
	}
	assertSVGSecurityHeaders(t, rangeResponse, true)

	conditionalRequest, _ := http.NewRequest(http.MethodGet, server.URL+"/api/v1/assets/malicious.svg", nil)
	conditionalRequest.Header.Set("If-None-Match", etag)
	conditionalResponse, err := http.DefaultClient.Do(conditionalRequest)
	if err != nil {
		t.Fatal(err)
	}
	conditionalResponse.Body.Close()
	if conditionalResponse.StatusCode != http.StatusNotModified {
		t.Fatalf("conditional status = %d", conditionalResponse.StatusCode)
	}
	assertSVGSecurityHeaders(t, conditionalResponse, false)
}

func assertSVGSecurityHeaders(t *testing.T, response *http.Response, expectContentType bool) {
	t.Helper()
	if got := response.Header.Get("Content-Type"); expectContentType && got != "image/svg+xml" {
		t.Fatalf("Content-Type = %q", got)
	}
	if got := response.Header.Get("Content-Disposition"); !strings.HasPrefix(got, "attachment;") {
		t.Fatalf("Content-Disposition = %q", got)
	}
	if got := response.Header.Get("Content-Security-Policy"); got != "sandbox; default-src 'none'" {
		t.Fatalf("Content-Security-Policy = %q", got)
	}
	if got := response.Header.Get("X-Content-Type-Options"); got != "nosniff" {
		t.Fatalf("X-Content-Type-Options = %q", got)
	}
}
