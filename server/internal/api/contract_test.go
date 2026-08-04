package api

import (
	"encoding/json"
	"os"
	"strings"
	"testing"

	"github.com/markrai/vaultist/server/internal/model"
)

func TestOpenAPIAndGoModelsShareRepresentativeFields(t *testing.T) {
	contract, err := os.ReadFile("../../../api/openapi.yaml")
	if err != nil {
		t.Fatalf("read OpenAPI: %v", err)
	}
	for _, expected := range []string{"NoteResponse:", "LinkOccurrence:", "Backlink:", "IndexState:", "revision:", "candidates:"} {
		if !strings.Contains(string(contract), expected) {
			t.Errorf("OpenAPI missing %q", expected)
		}
	}
	payload, err := json.Marshal(model.Note{
		ID: "Folder/Note", Path: "Folder/Note.md", Filename: "Note.md", Title: "Note",
		Revision: "sha256:abc", Links: []model.LinkOccurrence{{
			Kind: model.LinkWiki, Raw: "Other", Target: "Other", Line: 1, Column: 1,
			Resolution: model.Resolution{Status: model.LinkAmbiguous, Candidates: []model.Candidate{{ID: "A/Other", Title: "Other", Path: "A/Other.md"}}},
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	for _, expected := range []string{`"id"`, `"revision"`, `"resolution"`, `"candidates"`} {
		if !strings.Contains(string(payload), expected) {
			t.Errorf("Go JSON missing %s: %s", expected, payload)
		}
	}
}
