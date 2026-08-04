package search

import (
	"context"
	"os"
	"path/filepath"
	"testing"

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

	filesHits, err := Search(snapshot, "other", ModeFiles)
	if err != nil {
		t.Fatal(err)
	}
	if len(filesHits) != 1 || filesHits[0].ID != "Folder/Other" {
		t.Fatalf("files search = %#v", filesHits)
	}

	aliasHits, err := Search(snapshot, "start", ModeFiles)
	if err != nil {
		t.Fatal(err)
	}
	if len(aliasHits) != 1 || aliasHits[0].ID != "Alpha" {
		t.Fatalf("alias search = %#v", aliasHits)
	}

	contentHits, err := Search(snapshot, "folder/note", ModeContent)
	if err != nil {
		t.Fatal(err)
	}
	if len(contentHits) != 1 || contentHits[0].ID != "Folder/Note" {
		t.Fatalf("content search = %#v", contentHits)
	}

	contentMiss, err := Search(snapshot, "not-in-body", ModeContent)
	if err != nil {
		t.Fatal(err)
	}
	if len(contentMiss) != 0 {
		t.Fatalf("content miss = %#v", contentMiss)
	}

	_, err = Search(snapshot, "test", Mode("invalid"))
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

	hits, err := Search(snapshot, "shared", ModeContent)
	if err != nil {
		t.Fatal(err)
	}
	if len(hits) != 3 {
		t.Fatalf("hits = %d", len(hits))
	}
	if hits[0].ID != "A/One" || hits[1].ID != "B/Two" || hits[2].ID != "C/Three" {
		t.Fatalf("order = %#v", hits)
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
