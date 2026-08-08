package index

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"
)

func TestWriteNoteContentUpdatesFileAndRevision(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Note.md", "# Note\noriginal")
	manager := newTestManager(t, root)
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	updated, err := manager.WriteNoteContent(context.Background(), note.ID, note.Revision, []byte("# Note\nupdated"))
	if err != nil {
		t.Fatal(err)
	}
	if updated.Revision == note.Revision {
		t.Fatal("expected revision to change")
	}
	if updated.Revision != ContentRevision([]byte("# Note\nupdated")) {
		t.Fatalf("revision = %q", updated.Revision)
	}
	onDisk, err := os.ReadFile(filepath.Join(root, "Folder", "Note.md"))
	if err != nil {
		t.Fatal(err)
	}
	if string(onDisk) != "# Note\nupdated" {
		t.Fatalf("on disk = %q", onDisk)
	}
}

func TestWriteNoteContentAllowsUnindexedOnDiskNote(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Indexed.md", "# Indexed\n")
	manager := newTestManager(t, root)
	content := []byte("draft body")
	writeFile(t, root, "Folder/Unindexed.md", string(content))
	revision := ContentRevision(content)

	updated, err := manager.WriteNoteContent(context.Background(), "Folder/Unindexed", revision, []byte("saved body"))
	if err != nil {
		t.Fatal(err)
	}
	if updated.ID != "Folder/Unindexed" {
		t.Fatalf("id = %q", updated.ID)
	}
	if updated.Revision != ContentRevision([]byte("saved body")) {
		t.Fatalf("revision = %q", updated.Revision)
	}
	onDisk, err := os.ReadFile(filepath.Join(root, "Folder", "Unindexed.md"))
	if err != nil {
		t.Fatal(err)
	}
	if string(onDisk) != "saved body" {
		t.Fatalf("on disk = %q", onDisk)
	}
}

func TestDeleteNoteAllowsUnindexedOnDiskNote(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Indexed.md", "# Indexed\n")
	manager := newTestManager(t, root)
	content := []byte("to delete")
	writeFile(t, root, "Folder/Unindexed.md", string(content))
	revision := ContentRevision(content)

	if err := manager.DeleteNote(context.Background(), "Folder/Unindexed", revision); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(root, "Folder", "Unindexed.md")); !os.IsNotExist(err) {
		t.Fatalf("expected file removed, err=%v", err)
	}
}

func TestWriteNoteContentRejectsStaleRevision(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Note.md", "# Note\noriginal")
	manager := newTestManager(t, root)
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	_, err = manager.WriteNoteContent(context.Background(), note.ID, `"sha256:0000000000000000000000000000000000000000000000000000000000000000"`, []byte("# Note\nupdated"))
	if err == nil {
		t.Fatal("expected conflict")
	}
	conflict, ok := err.(*RevisionConflictError)
	if !ok {
		t.Fatalf("err = %T %v", err, err)
	}
	if conflict.Expected[:7] != "sha256:" || conflict.Actual != note.Revision {
		t.Fatalf("conflict = %#v", conflict)
	}
}

func TestDeleteNoteRemovesFile(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Note.md", "# Note\noriginal")
	manager := newTestManager(t, root)
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	if err := manager.DeleteNote(context.Background(), note.ID, note.Revision); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(root, "Folder", "Note.md")); !os.IsNotExist(err) {
		t.Fatalf("expected file removed, err=%v", err)
	}
}

func TestDeleteNoteRejectsStaleRevision(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Note.md", "# Note\noriginal")
	manager := newTestManager(t, root)
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	err = manager.DeleteNote(context.Background(), note.ID, `"sha256:0000000000000000000000000000000000000000000000000000000000000000"`)
	if err == nil {
		t.Fatal("expected conflict")
	}
	conflict, ok := err.(*RevisionConflictError)
	if !ok {
		t.Fatalf("err = %T %v", err, err)
	}
	if conflict.Expected[:7] != "sha256:" || conflict.Actual != note.Revision {
		t.Fatalf("conflict = %#v", conflict)
	}
}

func TestDeleteNoteNotFound(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Note.md", "# Note\noriginal")
	manager := newTestManager(t, root)
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	err = manager.DeleteNote(context.Background(), "Missing/Note", note.Revision)
	if err != ErrNotFound {
		t.Fatalf("err = %v", err)
	}
}

func TestCreateNoteWritesFileAndReturnsMetadata(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	content := []byte("# New Note\n\n")
	created, err := manager.CreateNote(context.Background(), "Folder/New Note", content)
	if err != nil {
		t.Fatal(err)
	}
	if created.ID != "Folder/New Note" {
		t.Fatalf("id = %q", created.ID)
	}
	if created.Revision != ContentRevision(content) {
		t.Fatalf("revision = %q", created.Revision)
	}
	onDisk, err := os.ReadFile(filepath.Join(root, "Folder", "New Note.md"))
	if err != nil {
		t.Fatal(err)
	}
	if string(onDisk) != string(content) {
		t.Fatalf("on disk = %q", onDisk)
	}
}

func TestCreateNoteRejectsExistingSnapshotNote(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Note.md", "# Note\noriginal")
	manager := newTestManager(t, root)
	_, err := manager.CreateNote(context.Background(), "Folder/Note", []byte("# duplicate"))
	if err != ErrNoteExists {
		t.Fatalf("err = %v", err)
	}
}

func TestCreateNoteRejectsExistingOnDiskFile(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Folder/Note.md", "# Note\noriginal")
	manager := newTestManager(t, root)
	writeFile(t, root, "Folder/Unindexed.md", "# unindexed\n")
	_, err := manager.CreateNote(context.Background(), "Folder/Unindexed", []byte("# new"))
	if err != ErrNoteExists {
		t.Fatalf("err = %v", err)
	}
}

func TestCreateNoteRejectsInvalidID(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	_, err := manager.CreateNote(context.Background(), "../secret", []byte(""))
	if err != ErrInvalidNoteID {
		t.Fatalf("err = %v", err)
	}
}

func TestCreateNoteRejectsHiddenID(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	if _, err := manager.CreateNote(context.Background(), ".trash/secret", []byte("hidden")); err != ErrInvalidNoteID {
		t.Fatalf("err = %v", err)
	}
}

func TestConcurrentCreateNoteIsExclusive(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	start := make(chan struct{})
	results := make(chan error, 2)
	var wait sync.WaitGroup
	for _, content := range []string{"first", "second"} {
		wait.Add(1)
		go func(content string) {
			defer wait.Done()
			<-start
			_, err := manager.CreateNote(context.Background(), "Concurrent", []byte(content))
			results <- err
		}(content)
	}
	close(start)
	wait.Wait()
	close(results)

	var successes, conflicts int
	for err := range results {
		switch {
		case err == nil:
			successes++
		case errors.Is(err, ErrNoteExists):
			conflicts++
		default:
			t.Fatalf("unexpected error: %v", err)
		}
	}
	if successes != 1 || conflicts != 1 {
		t.Fatalf("successes=%d conflicts=%d", successes, conflicts)
	}
}

func TestConcurrentWriteNoteContentHonorsRevision(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Note.md", "original")
	manager := newTestManager(t, root)
	note := manager.snapshot.Load().Notes["Note"]
	start := make(chan struct{})
	results := make(chan error, 2)
	var wait sync.WaitGroup
	for _, content := range []string{"first", "second"} {
		wait.Add(1)
		go func(content string) {
			defer wait.Done()
			<-start
			_, err := manager.WriteNoteContent(context.Background(), note.ID, note.Revision, []byte(content))
			results <- err
		}(content)
	}
	close(start)
	wait.Wait()
	close(results)

	var successes, conflicts int
	for err := range results {
		switch {
		case err == nil:
			successes++
		default:
			var conflict *RevisionConflictError
			if errors.As(err, &conflict) {
				conflicts++
			} else {
				t.Fatalf("unexpected error: %v", err)
			}
		}
	}
	if successes != 1 || conflicts != 1 {
		t.Fatalf("successes=%d conflicts=%d", successes, conflicts)
	}
}

func TestConcurrentUpdateAndDeleteAreSerializable(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Note.md", "original")
	manager := newTestManager(t, root)
	note := manager.snapshot.Load().Notes["Note"]
	start := make(chan struct{})
	results := make(chan error, 2)
	var wait sync.WaitGroup
	wait.Add(2)
	go func() {
		defer wait.Done()
		<-start
		_, err := manager.WriteNoteContent(context.Background(), note.ID, note.Revision, []byte("updated"))
		results <- err
	}()
	go func() {
		defer wait.Done()
		<-start
		results <- manager.DeleteNote(context.Background(), note.ID, note.Revision)
	}()
	close(start)
	wait.Wait()
	close(results)

	var successes, rejected int
	for err := range results {
		switch {
		case err == nil:
			successes++
		case errors.Is(err, ErrNotFound):
			rejected++
		default:
			var conflict *RevisionConflictError
			if errors.As(err, &conflict) {
				rejected++
			} else {
				t.Fatalf("unexpected error: %v", err)
			}
		}
	}
	if successes != 1 || rejected != 1 {
		t.Fatalf("successes=%d rejected=%d", successes, rejected)
	}
}

func TestCreateFolderCreatesDirectoryAndIndexesIt(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	name, path, err := manager.CreateFolder(context.Background(), "Projects/Ideas")
	if err != nil {
		t.Fatal(err)
	}
	if name != "Ideas" || path != "Projects/Ideas" {
		t.Fatalf("name=%q path=%q", name, path)
	}
	info, err := os.Stat(filepath.Join(root, "Projects", "Ideas"))
	if err != nil || !info.IsDir() {
		t.Fatalf("dir = %v err=%v", info, err)
	}
	waitForIndexedFolder(t, manager, "Projects/Ideas")
}

func waitForIndexedFolder(t *testing.T, manager *Manager, folderPath string) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		snapshot, err := manager.Current()
		if err == nil {
			if _, ok := snapshot.Folders[folderPath]; ok {
				return
			}
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatalf("timed out waiting for folder %q in snapshot", folderPath)
}

func TestCreateFolderRejectsExistingDirectory(t *testing.T) {
	root := t.TempDir()
	if err := os.MkdirAll(filepath.Join(root, "Projects"), 0o755); err != nil {
		t.Fatal(err)
	}
	manager := newTestManager(t, root)
	_, _, err := manager.CreateFolder(context.Background(), "Projects")
	if err != ErrFolderExists {
		t.Fatalf("err = %v", err)
	}
}

func TestCreateFolderRejectsConflictingNote(t *testing.T) {
	root := t.TempDir()
	writeFile(t, root, "Projects/Note.md", "# Note\n")
	manager := newTestManager(t, root)
	_, _, err := manager.CreateFolder(context.Background(), "Projects/Note")
	if err != ErrFolderExists {
		t.Fatalf("err = %v", err)
	}
}

func TestFolderMutationsRejectHiddenPath(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	if _, _, err := manager.CreateFolder(context.Background(), ".trash/Folder"); err != ErrInvalidFolder {
		t.Fatalf("create err = %v", err)
	}
	if err := manager.DeleteFolder(context.Background(), ".trash"); err != ErrInvalidFolder {
		t.Fatalf("delete err = %v", err)
	}
}

func TestConcurrentCreateFolderIsExclusive(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	start := make(chan struct{})
	results := make(chan error, 2)
	var wait sync.WaitGroup
	for range 2 {
		wait.Add(1)
		go func() {
			defer wait.Done()
			<-start
			_, _, err := manager.CreateFolder(context.Background(), "Projects")
			results <- err
		}()
	}
	close(start)
	wait.Wait()
	close(results)

	var successes, conflicts int
	for err := range results {
		switch {
		case err == nil:
			successes++
		case errors.Is(err, ErrFolderExists):
			conflicts++
		default:
			t.Fatalf("unexpected error: %v", err)
		}
	}
	if successes != 1 || conflicts != 1 {
		t.Fatalf("successes=%d conflicts=%d", successes, conflicts)
	}
}

func TestDeleteFolderRemovesEmptyDirectory(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	if _, _, err := manager.CreateFolder(context.Background(), "Projects/Ideas"); err != nil {
		t.Fatal(err)
	}
	if err := manager.DeleteFolder(context.Background(), "Projects/Ideas"); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(root, "Projects", "Ideas")); !os.IsNotExist(err) {
		t.Fatalf("expected folder removed, err=%v", err)
	}
}

func TestDeleteFolderRejectsNonEmptyDirectory(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	if _, _, err := manager.CreateFolder(context.Background(), "Projects/Ideas"); err != nil {
		t.Fatal(err)
	}
	writeFile(t, root, "Projects/Ideas/Note.md", "# Note\n")
	if err := manager.DeleteFolder(context.Background(), "Projects/Ideas"); err != ErrFolderNotEmpty {
		t.Fatalf("err = %v", err)
	}
}

func TestDeleteFolderNotFound(t *testing.T) {
	root := t.TempDir()
	manager := newTestManager(t, root)
	if err := manager.DeleteFolder(context.Background(), "Missing"); err != ErrNotFound {
		t.Fatalf("err = %v", err)
	}
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

func newTestManager(t *testing.T, root string) *Manager {
	t.Helper()
	manager, err := NewManager(root, "Test")
	if err != nil {
		t.Fatal(err)
	}
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	return manager
}
