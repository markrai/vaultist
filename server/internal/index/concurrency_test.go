package index

import (
	"context"
	"errors"
	"sync"
	"testing"
)

func TestStartRefreshRejectsConcurrent(t *testing.T) {
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
	if err := manager.StartRefresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	err = manager.StartRefresh(context.Background())
	if !errors.Is(err, ErrRefreshActive) {
		t.Fatalf("second StartRefresh = %v, want ErrRefreshActive", err)
	}
}

func TestCurrentDuringRefresh(t *testing.T) {
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
	var wg sync.WaitGroup
	for range 8 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for range 50 {
				_, _ = manager.Current()
			}
		}()
	}
	if err := manager.StartRefresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	wg.Wait()
}
