package search

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/markrai/vaultist/server/internal/index"
)

func TestSearchFilesAndContent(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, "Folder/Note.md", "# Note\nFolder/Note body text\n")
	writeTestFile(t, root, "Folder/Other.md", "# Other\nnothing here\n")
	writeTestFile(t, root, "Alpha.md", "---\ntitle: Welcome\naliases: [Start Here]\n---\n# Alpha\n")

	manager, err := index.NewManager(root, "Search Vault")
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

	filesHits, err := Search(context.Background(), snapshot, Request{Query: "other", Mode: ModeFiles})
	if err != nil {
		t.Fatal(err)
	}
	if len(filesHits.Hits) != 1 || filesHits.Hits[0].ID != "Folder/Other" {
		t.Fatalf("files search = %#v", filesHits.Hits)
	}

	aliasHits, err := Search(context.Background(), snapshot, Request{Query: "start", Mode: ModeFiles})
	if err != nil {
		t.Fatal(err)
	}
	if len(aliasHits.Hits) != 1 || aliasHits.Hits[0].ID != "Alpha" {
		t.Fatalf("alias search = %#v", aliasHits.Hits)
	}

	contentHits, err := Search(context.Background(), snapshot, Request{Query: "folder/note", Mode: ModeContent})
	if err != nil {
		t.Fatal(err)
	}
	if len(contentHits.Hits) != 1 || contentHits.Hits[0].ID != "Folder/Note" {
		t.Fatalf("content search = %#v", contentHits.Hits)
	}

	contentMiss, err := Search(context.Background(), snapshot, Request{Query: "not-in-body", Mode: ModeContent})
	if err != nil {
		t.Fatal(err)
	}
	if len(contentMiss.Hits) != 0 {
		t.Fatalf("content miss = %#v", contentMiss.Hits)
	}

	_, err = Search(context.Background(), snapshot, Request{Query: "test", Mode: Mode("invalid")})
	if err != ErrInvalidMode {
		t.Fatalf("invalid mode = %v", err)
	}
}

func TestSearchPreservesDeterministicOrder(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, "A/One.md", "# One\nshared term\n")
	writeTestFile(t, root, "B/Two.md", "# Two\nshared term\n")
	writeTestFile(t, root, "C/Three.md", "# Three\nshared term\n")

	manager, _ := index.NewManager(root, "")
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	snapshot, _ := manager.Current()

	hits, err := Search(context.Background(), snapshot, Request{Query: "shared", Mode: ModeContent})
	if err != nil {
		t.Fatal(err)
	}
	if len(hits.Hits) != 3 {
		t.Fatalf("hits = %d", len(hits.Hits))
	}
	if hits.Hits[0].ID != "A/One" || hits.Hits[1].ID != "B/Two" || hits.Hits[2].ID != "C/Three" {
		t.Fatalf("order = %#v", hits.Hits)
	}
}

func TestContentSearchUsesIndexNotDisk(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, "Indexed.md", "# Indexed\nfindme token\n")

	manager, err := index.NewManager(root, "Disk Test")
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

	if err := os.Remove(filepath.Join(root, "Indexed.md")); err != nil {
		t.Fatal(err)
	}

	hits, err := Search(context.Background(), snapshot, Request{Query: "findme", Mode: ModeContent})
	if err != nil {
		t.Fatal(err)
	}
	if len(hits.Hits) != 1 || hits.Hits[0].ID != "Indexed" {
		t.Fatalf("content search after delete = %#v", hits.Hits)
	}
}

func TestSearchRespectsContextCancel(t *testing.T) {
	if testing.Short() {
		t.Skip("large vault setup")
	}
	root := t.TempDir()
	for i := range 200 {
		writeTestFile(t, root, fmt.Sprintf("Notes/Note%03d.md", i), "# Note\nneedle in haystack\n")
	}

	manager, err := index.NewManager(root, "Cancel Test")
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

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err = Search(ctx, snapshot, Request{Query: "needle", Mode: ModeContent})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("cancel = %v", err)
	}
}

func TestSearchLargeVault(t *testing.T) {
	if testing.Short() {
		t.Skip("large vault setup")
	}
	root := t.TempDir()
	for i := range 500 {
		writeTestFile(t, root, fmt.Sprintf("Bulk/Note%03d.md", i), "# Bulk\nshared bulk term\n")
	}

	manager, err := index.NewManager(root, "Large Vault")
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

	start := time.Now()
	hits, err := Search(context.Background(), snapshot, Request{Query: "shared", Mode: ModeContent})
	if err != nil {
		t.Fatal(err)
	}
	if len(hits.Hits) != 500 {
		t.Fatalf("hits = %d", len(hits.Hits))
	}
	if elapsed := time.Since(start); elapsed > 2*time.Second {
		t.Fatalf("search took %v", elapsed)
	}
}

func writeTestFile(t *testing.T, root, relative, content string) {
	t.Helper()
	filePath := filepath.Join(root, filepath.FromSlash(relative))
	if err := os.MkdirAll(filepath.Dir(filePath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filePath, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}
