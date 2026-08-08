package vault

import (
	"os"
	"path/filepath"
	"strconv"
	"sync"
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

func TestVaultOperationsRejectSymlinkedParentEscape(t *testing.T) {
	root := t.TempDir()
	outside := t.TempDir()
	link := filepath.Join(root, "escape")
	if err := os.Symlink(outside, link); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}

	if err := ReplaceFileAtomically(root, "escape/replaced.md", []byte("nope")); err == nil {
		t.Fatal("expected replace beneath symlinked parent to fail")
	}
	if err := CreateFileAtomically(root, "escape/created.md", []byte("nope")); err == nil {
		t.Fatal("expected create beneath symlinked parent to fail")
	}
	if err := MkdirInside(root, "escape/folder"); err == nil {
		t.Fatal("expected mkdir beneath symlinked parent to fail")
	}
	for _, name := range []string{"replaced.md", "created.md", "folder"} {
		if _, err := os.Stat(filepath.Join(outside, name)); !os.IsNotExist(err) {
			t.Fatalf("outside path %q was touched: %v", name, err)
		}
	}
}

func TestVaultOperationsRejectExistingSymlinkEscape(t *testing.T) {
	root := t.TempDir()
	outside := filepath.Join(t.TempDir(), "secret.md")
	if err := os.WriteFile(outside, []byte("secret"), 0o600); err != nil {
		t.Fatal(err)
	}
	link := filepath.Join(root, "linked.md")
	if err := os.Symlink(outside, link); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}

	if _, err := OpenFileInside(root, "linked.md"); err == nil {
		t.Fatal("expected symlink open to fail")
	}
	if err := ReplaceFileAtomically(root, "linked.md", []byte("changed")); err == nil {
		t.Fatal("expected symlink replace to fail")
	}
	if err := DeleteFileInside(root, "linked.md"); err == nil {
		t.Fatal("expected symlink delete to fail")
	}
	content, err := os.ReadFile(outside)
	if err != nil || string(content) != "secret" {
		t.Fatalf("outside content = %q err=%v", content, err)
	}
}

func TestCreateFileDoesNotEscapeDuringSymlinkSwap(t *testing.T) {
	root := t.TempDir()
	outside := t.TempDir()
	swapPath := filepath.Join(root, "swap")
	if err := os.Symlink(outside, swapPath); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	if err := os.Remove(swapPath); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(swapPath, 0o755); err != nil {
		t.Fatal(err)
	}

	stop := make(chan struct{})
	var wait sync.WaitGroup
	wait.Add(1)
	go func() {
		defer wait.Done()
		for {
			select {
			case <-stop:
				return
			default:
			}
			_ = os.Remove(swapPath)
			_ = os.Symlink(outside, swapPath)
			_ = os.Remove(swapPath)
			_ = os.Mkdir(swapPath, 0o755)
		}
	}()
	for i := range 200 {
		name := filepath.ToSlash(filepath.Join("swap", "note-"+strconv.Itoa(i)+".md"))
		if err := CreateFileAtomically(root, name, []byte("inside")); err == nil {
			_ = DeleteFileInside(root, name)
		}
	}
	close(stop)
	wait.Wait()

	entries, err := os.ReadDir(outside)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 0 {
		t.Fatalf("vault operation escaped root: %#v", entries)
	}
}

func TestCreateFileAtomicallyDoesNotReplaceExistingFile(t *testing.T) {
	root := t.TempDir()
	if err := CreateFileAtomically(root, "Note.md", []byte("first")); err != nil {
		t.Fatal(err)
	}
	if err := CreateFileAtomically(root, "Note.md", []byte("second")); err != ErrPathOccupied {
		t.Fatalf("err = %v", err)
	}
	content, err := os.ReadFile(filepath.Join(root, "Note.md"))
	if err != nil || string(content) != "first" {
		t.Fatalf("content = %q err=%v", content, err)
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
