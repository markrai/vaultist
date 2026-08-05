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

func TestDeleteFileInsideRemovesFile(t *testing.T) {
	root := t.TempDir()
	if err := ReplaceFileAtomically(root, "Notes/Test.md", []byte("content")); err != nil {
		t.Fatal(err)
	}
	if err := DeleteFileInside(root, "Notes/Test.md"); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(root, "Notes", "Test.md")); !os.IsNotExist(err) {
		t.Fatalf("expected file removed, err=%v", err)
	}
}

func TestDeleteFileInsideRejectsTraversal(t *testing.T) {
	root := t.TempDir()
	if err := DeleteFileInside(root, "../escape.md"); err == nil {
		t.Fatal("expected traversal rejection")
	}
}
