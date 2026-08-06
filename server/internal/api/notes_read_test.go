package api

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"testing"

	"github.com/markrai/vaultist/server/internal/index"
)

func TestGetNoteRevisionMatchesOnDiskContentWhenSnapshotStale(t *testing.T) {
	manager, server := contractFixtureWithManager(t)
	defer server.Close()

	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Folder/Note"]
	snapshotRevision := note.Revision

	updated := "# Note\nupdated on disk without reindex"
	filePath := filepath.Join(manager.Root(), filepath.FromSlash("Folder/Note.md"))
	if err := os.WriteFile(filePath, []byte(updated), 0o644); err != nil {
		t.Fatal(err)
	}

	payload, status := fetchResponse(t, http.MethodGet, server.URL+"/api/v1/notes/Folder/Note", nil)
	if status != http.StatusOK {
		t.Fatalf("status = %d body=%s", status, payload)
	}
	var response map[string]any
	if err := json.Unmarshal(payload, &response); err != nil {
		t.Fatal(err)
	}
	if response["content"] != updated {
		t.Fatalf("content = %#v", response["content"])
	}
	expectedRevision := index.ContentRevision([]byte(updated))
	if response["revision"] != expectedRevision {
		t.Fatalf("revision = %#v want %q", response["revision"], expectedRevision)
	}
	if response["revision"] == snapshotRevision {
		t.Fatal("expected revision to reflect on-disk content, not stale snapshot metadata")
	}
}
