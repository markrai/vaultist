package index

import (
	"context"
	"os"
	"path/filepath"
	"testing"
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
