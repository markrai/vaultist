package index

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	mdparser "github.com/markrai/vaultist/server/internal/markdown"
	"github.com/markrai/vaultist/server/internal/model"
)

var (
	ErrNotReady      = errors.New("index not ready")
	ErrRefreshActive = errors.New("index refresh already running")
	ErrNotFound      = errors.New("not found")
)

type Manager struct {
	root      string
	vaultName string
	parser    *mdparser.Parser
	snapshot  atomic.Pointer[Snapshot]
	mu        sync.Mutex
	state     model.IndexState
	cancel    context.CancelFunc
	log       *slog.Logger
	refreshHook func()
}

func NewManager(root, vaultName string) (*Manager, error) {
	root = strings.TrimSpace(root)
	if root == "" {
		return nil, fmt.Errorf("vault root is required")
	}
	abs, err := filepath.Abs(root)
	if err != nil {
		return nil, fmt.Errorf("resolve vault root: %w", err)
	}
	if strings.TrimSpace(vaultName) == "" {
		vaultName = filepath.Base(abs)
	}
	return &Manager{
		root: abs, vaultName: vaultName, parser: mdparser.NewParser(),
		state: model.IndexState{State: "not_ready"},
		log:   slog.Default(),
	}, nil
}

func (m *Manager) Root() string { return m.root }

func (m *Manager) Current() (*Snapshot, error) {
	snapshot := m.snapshot.Load()
	if snapshot == nil {
		return nil, ErrNotReady
	}
	return snapshot, nil
}

func (m *Manager) State() model.IndexState {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.state
}

// SetRefreshHook runs hook at the start of each refresh, before building the index.
// It is intended for tests that need to observe or hold the indexing state.
func (m *Manager) SetRefreshHook(hook func()) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.refreshHook = hook
}

func (m *Manager) StartRefresh(parent context.Context) error {
	m.mu.Lock()
	if m.state.State == "indexing" {
		m.mu.Unlock()
		return ErrRefreshActive
	}
	ctx, cancel := context.WithCancel(parent)
	m.cancel = cancel
	m.state.State = "indexing"
	m.state.StartedAt = time.Now().UTC()
	startedAt := m.state.StartedAt
	m.mu.Unlock()
	m.logger().Info("index_refresh_start")
	go func() {
		defer cancel()
		_ = m.refresh(ctx, startedAt)
	}()
	return nil
}

func (m *Manager) Refresh(ctx context.Context) error {
	m.mu.Lock()
	if m.state.State == "indexing" {
		m.mu.Unlock()
		return ErrRefreshActive
	}
	m.state.State = "indexing"
	m.state.StartedAt = time.Now().UTC()
	startedAt := m.state.StartedAt
	m.mu.Unlock()
	m.logger().Info("index_refresh_start")
	return m.refresh(ctx, startedAt)
}

func (m *Manager) logger() *slog.Logger {
	if m.log != nil {
		return m.log
	}
	return slog.Default()
}

func (m *Manager) Close() {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.cancel != nil {
		m.cancel()
	}
}

func (m *Manager) refresh(ctx context.Context, startedAt time.Time) error {
	m.mu.Lock()
	hook := m.refreshHook
	m.mu.Unlock()
	if hook != nil {
		hook()
	}
	previous := m.snapshot.Load()
	generation := uint64(1)
	if previous != nil {
		generation = previous.Generation + 1
	}
	next, err := build(ctx, m.root, m.vaultName, generation, m.parser)
	now := time.Now().UTC()
	duration := now.Sub(startedAt).Milliseconds()
	m.mu.Lock()
	defer m.mu.Unlock()
	m.cancel = nil
	if err != nil {
		if previous == nil {
			m.state.State = "unavailable"
		} else {
			m.state.State = "ready"
		}
		m.state.FinishedAt = now
		m.logger().Info("index_refresh_fail",
			"duration_ms", duration,
			"error_code", classifyRefreshError(err),
		)
		return err
	}
	m.snapshot.Store(next)
	errorCount := 0
	for _, note := range next.Notes {
		if note.Error != "" {
			errorCount++
		}
	}
	m.state = model.IndexState{
		State: "ready", Generation: generation, StartedAt: startedAt,
		FinishedAt: now, NoteCount: len(next.Notes), AssetCount: len(next.Assets),
		ErrorCount: errorCount,
	}
	m.logger().Info("index_refresh_complete",
		"generation", generation,
		"note_count", len(next.Notes),
		"asset_count", len(next.Assets),
		"error_count", errorCount,
		"duration_ms", duration,
	)
	return nil
}
