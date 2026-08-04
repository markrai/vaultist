package index

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestBuildPopulatesSearchBlobs(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, "Good.md", "# Good\nbody text here\n")
	writeTestFile(t, root, "Alias.md", "---\ntitle: Welcome\naliases: [Start Here]\n---\n# Alias\n")

	manager, err := NewManager(root, "Blob Test")
	if err != nil {
		t.Fatal(err)
	}
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}

	good := snapshot.SearchBlobs["Good"]
	if !strings.Contains(good.Content, "body text here") {
		t.Fatalf("content blob = %q", good.Content)
	}
	if good.Files == "" {
		t.Fatal("expected files blob")
	}

	alias := snapshot.SearchBlobs["Alias"]
	if !strings.Contains(alias.Files, "start here") {
		t.Fatalf("files blob = %q", alias.Files)
	}

	oversized := filepath.Join(root, "Huge.md")
	if err := os.WriteFile(oversized, []byte(strings.Repeat("x", maxIndexNoteBytes+1)), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	snapshot, err = manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	if snapshot.Notes["Huge"].Error == "" {
		t.Fatal("expected huge note error")
	}
	if _, ok := snapshot.SearchBlobs["Huge"]; ok {
		t.Fatal("expected no search blob for errored note")
	}
}
