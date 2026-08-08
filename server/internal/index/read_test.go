package index

import (
	"context"
	"testing"
)

func TestGetNoteForReadUnindexedOnDiskNote(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, "Folder/Fresh.md", "# Fresh\nnew body")
	manager, err := NewManager(root, "Test")
	if err != nil {
		t.Fatal(err)
	}
	defer manager.Close()
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}

	note, content, err := manager.GetNoteForRead("Folder/Fresh")
	if err != nil {
		t.Fatal(err)
	}
	if string(content) != "# Fresh\nnew body" {
		t.Fatalf("content = %q", content)
	}
	if note.ID != "Folder/Fresh" {
		t.Fatalf("id = %q", note.ID)
	}
	if note.Revision != ContentRevision(content) {
		t.Fatalf("revision = %q", note.Revision)
	}
}

func TestGetNoteForReadMissingNote(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, "Note.md", "# Note")
	manager, err := NewManager(root, "Test")
	if err != nil {
		t.Fatal(err)
	}
	defer manager.Close()
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}

	_, _, err = manager.GetNoteForRead("Missing")
	if err != ErrNotFound {
		t.Fatalf("err = %v", err)
	}
}

func TestGetNoteForReadRejectsHiddenOnDiskNote(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, ".trash/Secret.md", "hidden")
	manager, err := NewManager(root, "Test")
	if err != nil {
		t.Fatal(err)
	}
	defer manager.Close()
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}

	_, _, err = manager.GetNoteForRead(".trash/Secret")
	if err != ErrInvalidNoteID {
		t.Fatalf("err = %v", err)
	}
}
