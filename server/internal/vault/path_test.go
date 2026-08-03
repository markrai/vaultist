package vault

import "testing"

func TestStableNoteIDAndTraversalRejection(t *testing.T) {
	id, err := NoteID("Projects/Über Vega.md")
	if err != nil || id != "Projects/Über Vega" {
		t.Fatalf("id=%q err=%v", id, err)
	}
	for _, invalid := range []string{"../secret.md", "folder/../../secret.md", "C:/secret.md", "", "not-markdown.png"} {
		if _, err := NoteID(invalid); err == nil {
			t.Errorf("NoteID(%q) unexpectedly succeeded", invalid)
		}
	}
}

func TestHiddenDirectory(t *testing.T) {
	for _, hidden := range []string{".obsidian/app.json", ".trash/deleted.md", "folder/.cache/value"} {
		if !IsHidden(hidden) {
			t.Errorf("%q should be hidden", hidden)
		}
	}
	if IsHidden("Projects/Vega.md") {
		t.Fatal("normal path marked hidden")
	}
}
