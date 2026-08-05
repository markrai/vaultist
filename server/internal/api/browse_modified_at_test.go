package api

import (
	"testing"
	"time"
)

func TestBrowseAndSearchItemsIncludeModifiedAt(t *testing.T) {
	server := contractFixture(t)
	defer server.Close()

	browse := getJSON(t, server.URL+"/api/v1/notes?limit=100")
	items := browse["items"].([]any)
	if len(items) == 0 {
		t.Fatal("expected browse items")
	}
	var sawNote bool
	for _, raw := range items {
		item := raw.(map[string]any)
		if item["kind"] != "note" {
			if _, ok := item["modifiedAt"]; ok {
				t.Fatalf("folder item should omit modifiedAt: %#v", item)
			}
			continue
		}
		sawNote = true
		modifiedAt, ok := item["modifiedAt"].(string)
		if !ok || modifiedAt == "" {
			t.Fatalf("note item missing modifiedAt: %#v", item)
		}
		if _, err := time.Parse(time.RFC3339Nano, modifiedAt); err != nil {
			if _, err := time.Parse(time.RFC3339, modifiedAt); err != nil {
				t.Fatalf("modifiedAt %q is not RFC3339: %v", modifiedAt, err)
			}
		}
	}
	if !sawNote {
		t.Fatal("expected at least one note item in browse response")
	}

	search := getJSON(t, server.URL+"/api/v1/search?q=other&limit=10")
	searchItems := search["items"].([]any)
	if len(searchItems) != 1 {
		t.Fatalf("search items = %d", len(searchItems))
	}
	searchItem := searchItems[0].(map[string]any)
	modifiedAt, ok := searchItem["modifiedAt"].(string)
	if !ok || modifiedAt == "" {
		t.Fatalf("search note missing modifiedAt: %#v", searchItem)
	}
}
