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

func TestMkdirInsideCreatesDirectory(t *testing.T) {
	root := t.TempDir()
	if err := MkdirInside(root, "Projects/Ideas"); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(filepath.Join(root, "Projects", "Ideas"))
	if err != nil || !info.IsDir() {
		t.Fatalf("dir = %v err=%v", info, err)
	}
}

func TestMkdirInsideRejectsExistingDirectory(t *testing.T) {
	root := t.TempDir()
	if err := MkdirInside(root, "Projects"); err != nil {
		t.Fatal(err)
	}
	if err := MkdirInside(root, "Projects"); err != ErrFolderExists {
		t.Fatalf("err = %v", err)
	}
}

func TestMkdirInsideRejectsOccupiedFile(t *testing.T) {
	root := t.TempDir()
	if err := ReplaceFileAtomically(root, "Projects", []byte("not a dir")); err != nil {
		t.Fatal(err)
	}
	if err := MkdirInside(root, "Projects"); err != ErrPathOccupied {
		t.Fatalf("err = %v", err)
	}
}

func TestRemoveDirInsideDeletesEmptyDirectory(t *testing.T) {
	root := t.TempDir()
	if err := MkdirInside(root, "Projects/Ideas"); err != nil {
		t.Fatal(err)
	}
	if err := RemoveDirInside(root, "Projects/Ideas"); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(root, "Projects", "Ideas")); !os.IsNotExist(err) {
		t.Fatalf("expected folder removed, err=%v", err)
	}
}

func TestRemoveDirInsideRejectsNonEmptyDirectory(t *testing.T) {
	root := t.TempDir()
	if err := MkdirInside(root, "Projects/Ideas"); err != nil {
		t.Fatal(err)
	}
	if err := ReplaceFileAtomically(root, "Projects/Ideas/Note.md", []byte("# Note")); err != nil {
		t.Fatal(err)
	}
	if err := RemoveDirInside(root, "Projects/Ideas"); err != ErrFolderNotEmpty {
		t.Fatalf("err = %v", err)
	}
}

func TestRemoveDirInsideRejectsMissingDirectory(t *testing.T) {
	root := t.TempDir()
	if err := RemoveDirInside(root, "Missing"); err == nil {
		t.Fatal("expected missing folder error")
	}
}
