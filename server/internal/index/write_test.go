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
	if updated.Revision != contentRevision([]byte("# Note\nupdated")) {
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
