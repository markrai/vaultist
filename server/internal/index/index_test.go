package index

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/markrai/vaultist/server/internal/model"
)

func TestIndexedNoteModifiedAtFromMtime(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, "Timed.md", "# Timed")
	filePath := filepath.Join(root, "Timed.md")
	want := time.Date(2026, 8, 5, 17, 32, 54, 0, time.UTC)
	if err := os.Chtimes(filePath, want, want); err != nil {
		t.Fatal(err)
	}

	manager, err := NewManager(root, "Test Vault")
	if err != nil {
		t.Fatal(err)
	}
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	note := snapshot.Notes["Timed"]
	if note == nil {
		t.Fatal("Timed note missing from index")
	}
	if !note.ModifiedAt.UTC().Truncate(time.Second).Equal(want) {
		t.Fatalf("ModifiedAt = %v, want %v", note.ModifiedAt, want)
	}
}

func TestIndexRejectsSymlinkedObsidianConfiguration(t *testing.T) {
	root := t.TempDir()
	outside := t.TempDir()
	writeTestFile(t, outside, "app.json", `{"attachmentFolderPath":"outside"}`)
	if err := os.Symlink(outside, filepath.Join(root, ".obsidian")); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	writeTestFile(t, root, "Home.md", "# Home")

	manager, err := NewManager(root, "Test Vault")
	if err != nil {
		t.Fatal(err)
	}
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	if snapshot.AttachmentFolder != "" {
		t.Fatalf("attachment folder escaped vault: %q", snapshot.AttachmentFolder)
	}
}

func TestIndexResolutionBacklinksAssetsAndRefresh(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, ".obsidian/app.json", `{"attachmentFolderPath":"attachments"}`)
	writeTestFile(t, root, ".trash/Ignored.md", "# no")
	writeTestFile(t, root, "Home.md", "---\ntitle: Welcome\naliases: [Start Here]\n---\n# Home\n[[Projects/Vega]] and [[Vega]] and [[Missing]]\n![[logo.png]]\n")
	writeTestFile(t, root, "Projects/Vega.md", "# Vega\n[[Home]]\n[[Home|again]]\n")
	writeTestFile(t, root, "Archive/Vega.md", "# Old Vega")
	writeTestFile(t, root, "Unicode/Über Note.md", "# Unicode")
	writeTestFile(t, root, "attachments/logo.png", "not-a-real-png")

	manager, err := NewManager(root, "Test Vault")
	if err != nil {
		t.Fatal(err)
	}
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	if len(snapshot.Notes) != 4 {
		t.Fatalf("notes = %d", len(snapshot.Notes))
	}
	if snapshot.Notes["Home"].Title != "Welcome" {
		t.Fatalf("home = %#v", snapshot.Notes["Home"])
	}
	if snapshot.Notes["Home"].Revision[:7] != "sha256:" {
		t.Fatalf("revision = %q", snapshot.Notes["Home"].Revision)
	}

	exact := snapshot.ResolveNote("Home.md", "Projects/Vega", false)
	if exact.Status != model.LinkResolved || exact.NoteID != "Projects/Vega" {
		t.Fatalf("exact = %#v", exact)
	}
	ambiguous := snapshot.ResolveNote("Home.md", "Vega", false)
	if ambiguous.Status != model.LinkAmbiguous || len(ambiguous.Candidates) != 2 {
		t.Fatalf("ambiguous = %#v", ambiguous)
	}
	alias := snapshot.ResolveNote("Projects/Vega.md", "Start Here", false)
	if alias.Status != model.LinkResolved || alias.NoteID != "Home" {
		t.Fatalf("alias = %#v", alias)
	}
	missing := snapshot.ResolveNote("Home.md", "Nothing", false)
	if missing.Status != model.LinkMissing {
		t.Fatalf("missing = %#v", missing)
	}
	unicode := snapshot.ResolveNote("Home.md", "Unicode/%C3%9Cber Note", false)
	if unicode.Status != model.LinkMissing {
		t.Fatalf("encoded input is decoded by parser/API before resolver: %#v", unicode)
	}
	unicode = snapshot.ResolveNote("Home.md", "Unicode/Über Note", false)
	if unicode.Status != model.LinkResolved || unicode.NoteID != "Unicode/Über Note" {
		t.Fatalf("unicode = %#v", unicode)
	}

	asset := snapshot.ResolveAsset("Home.md", "logo.png")
	if asset.Status != model.LinkResolved || asset.AssetID != "attachments/logo.png" {
		t.Fatalf("asset = %#v", asset)
	}
	if len(snapshot.Backlinks["Home"]) != 2 {
		t.Fatalf("backlinks = %#v", snapshot.Backlinks["Home"])
	}
	if len(snapshot.Backlinks["Projects/Vega"]) != 1 {
		t.Fatalf("vega backlinks = %#v", snapshot.Backlinks["Projects/Vega"])
	}

	firstGeneration := snapshot.Generation
	writeTestFile(t, root, "New Note.md", "[[Home]]")
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	refreshed, _ := manager.Current()
	if refreshed.Generation != firstGeneration+1 || refreshed.Notes["New Note"] == nil {
		t.Fatalf("refresh = %#v", refreshed)
	}
}

func TestRelativeAssetAndPathTraversal(t *testing.T) {
	root := t.TempDir()
	writeTestFile(t, root, "Folder/Note.md", "![x](images/pic.jpg) ![up](../shared.png) [Home](../Home.md)")
	writeTestFile(t, root, "Folder/images/pic.jpg", "jpeg")
	writeTestFile(t, root, "shared.png", "png")
	writeTestFile(t, root, "Home.md", "# Home")
	manager, _ := NewManager(root, "")
	if err := manager.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	snapshot, _ := manager.Current()
	resolution := snapshot.ResolveAsset("Folder/Note.md", "images/pic.jpg")
	if resolution.AssetID != "Folder/images/pic.jpg" {
		t.Fatalf("resolution = %#v", resolution)
	}
	parentAsset := snapshot.ResolveAsset("Folder/Note.md", "../shared.png")
	if parentAsset.AssetID != "shared.png" {
		t.Fatalf("parent asset = %#v", parentAsset)
	}
	parentNote := snapshot.ResolveNote("Folder/Note.md", "../Home.md", true)
	if parentNote.NoteID != "Home" {
		t.Fatalf("parent note = %#v", parentNote)
	}
	if got := snapshot.ResolveAsset("Folder/Note.md", "../../../outside.png"); got.Status != model.LinkMissing {
		t.Fatalf("traversal = %#v", got)
	}
}

func writeTestFile(t *testing.T, root, relative, content string) {
	t.Helper()
	filePath := filepath.Join(root, filepath.FromSlash(relative))
	if err := os.MkdirAll(filepath.Dir(filePath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filePath, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}
