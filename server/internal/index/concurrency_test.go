package index

import (
	"context"
	"sync"
	"testing"
	"time"
)

func TestStartRefreshCoalescesConcurrent(t *testing.T) {
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
	release := holdRefreshDuring(t, manager)
	defer release()

	if err := manager.StartRefresh(context.Background()); err != nil {
		t.Fatalf("first StartRefresh during active refresh = %v", err)
	}
	if err := manager.StartRefresh(context.Background()); err != nil {
		t.Fatalf("second StartRefresh during active refresh = %v", err)
	}
	manager.mu.Lock()
	pending := manager.pendingRefresh
	manager.mu.Unlock()
	if !pending {
		t.Fatal("expected pendingRefresh to be set")
	}
}

func TestStartRefreshFollowUpIndexesLaterWrite(t *testing.T) {
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
	firstGen := manager.State().Generation

	release := holdRefreshDuring(t, manager)
	writeTestFile(t, root, "Later.md", "# Later")
	if err := manager.StartRefresh(context.Background()); err != nil {
		t.Fatalf("StartRefresh during active refresh = %v", err)
	}
	release()

	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		state := manager.State()
		if state.State == "ready" && state.Generation > firstGen+1 {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	snapshot, err := manager.Current()
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := snapshot.Notes["Later"]; !ok {
		t.Fatalf("notes = %#v", snapshot.Notes)
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

func holdRefreshDuring(t *testing.T, manager *Manager) func() {
	t.Helper()
	entered := make(chan struct{})
	done := make(chan struct{})
	manager.SetRefreshHook(func() {
		close(entered)
		<-done
	})
	if err := manager.StartRefresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	select {
	case <-entered:
	case <-time.After(2 * time.Second):
		t.Fatal("timed out waiting for refresh to start")
	}
	return func() {
		close(done)
		manager.SetRefreshHook(nil)
	}
}
