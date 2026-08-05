package index

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"path/filepath"
	"strings"
	"sync"
	"testing"
)

type captureHandler struct {
	mu      sync.Mutex
	records []slog.Record
}

func (h *captureHandler) Enabled(context.Context, slog.Level) bool { return true }

func (h *captureHandler) Handle(_ context.Context, record slog.Record) error {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.records = append(h.records, record.Clone())
	return nil
}

func (h *captureHandler) WithAttrs([]slog.Attr) slog.Handler { return h }

func (h *captureHandler) WithGroup(string) slog.Handler { return h }

func (h *captureHandler) find(msg string) (slog.Record, bool) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for _, record := range h.records {
		if record.Message == msg {
			return record, true
		}
	}
	return slog.Record{}, false
}

func attrString(record slog.Record, key string) (string, bool) {
	var value string
	found := false
	record.Attrs(func(attr slog.Attr) bool {
		if attr.Key == key {
			value = attr.Value.String()
			found = true
			return false
		}
		return true
	})
	return value, found
}

func TestClassifyRefreshError(t *testing.T) {
	t.Parallel()

	absPath := filepath.Join(t.TempDir(), "secret", "note.md")
	pathErr := fmt.Errorf("Rel: can't make %s relative to %s", absPath, filepath.Join(t.TempDir(), "vault"))

	tests := []struct {
		name string
		err  error
		want string
	}{
		{name: "canceled", err: context.Canceled, want: "canceled"},
		{name: "deadline", err: context.DeadlineExceeded, want: "deadline_exceeded"},
		{name: "vault unavailable", err: ErrVaultUnavailable, want: "vault_unavailable"},
		{name: "index build", err: ErrIndexBuild, want: "build_failed"},
		{name: "path style", err: pathErr, want: "build_failed"},
	}

	for _, tc := range tests {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			t.Parallel()
			got := classifyRefreshError(tc.err)
			if got != tc.want {
				t.Fatalf("classifyRefreshError() = %q, want %q", got, tc.want)
			}
			if strings.Contains(got, absPath) {
				t.Fatalf("classifyRefreshError() = %q, must not contain vault path", got)
			}
		})
	}
}

func TestRefreshFailLogsErrorCodeOnly(t *testing.T) {
	root := filepath.Join(t.TempDir(), "missing-vault")
	manager, err := NewManager(root, "Test Vault")
	if err != nil {
		t.Fatal(err)
	}

	handler := &captureHandler{}
	manager.log = slog.New(handler)

	if err := manager.Refresh(context.Background()); !errors.Is(err, ErrVaultUnavailable) {
		t.Fatalf("Refresh() = %v, want ErrVaultUnavailable", err)
	}

	record, ok := handler.find("index_refresh_fail")
	if !ok {
		t.Fatal("expected index_refresh_fail log record")
	}

	if code, ok := attrString(record, "error_code"); !ok || code != "vault_unavailable" {
		t.Fatalf("error_code = %q, want vault_unavailable", code)
	}
	if _, ok := attrString(record, "error"); ok {
		t.Fatal("index_refresh_fail must not log raw error attribute")
	}
	if _, ok := attrString(record, "duration_ms"); !ok {
		t.Fatal("index_refresh_fail must log duration_ms")
	}
}
