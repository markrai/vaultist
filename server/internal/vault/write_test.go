package vault

import (
	"os"
	"path/filepath"
	"testing"
)

func TestReplaceFileAtomicallyWritesAndReplaces(t *testing.T) {
	root := t.TempDir()
	if err := ReplaceFileAtomically(root, "Notes/Test.md", []byte("first")); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(root, "Notes", "Test.md")
	first, err := os.ReadFile(path)
	if err != nil || string(first) != "first" {
		t.Fatalf("first write = %q err=%v", first, err)
	}
	if err := ReplaceFileAtomically(root, "Notes/Test.md", []byte("second")); err != nil {
		t.Fatal(err)
	}
	second, err := os.ReadFile(path)
	if err != nil || string(second) != "second" {
		t.Fatalf("replace = %q err=%v", second, err)
	}
}

func TestReplaceFileAtomicallyRejectsTraversal(t *testing.T) {
	root := t.TempDir()
	if err := ReplaceFileAtomically(root, "../escape.md", []byte("nope")); err == nil {
		t.Fatal("expected traversal rejection")
	}
}
