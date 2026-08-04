package index

import (
	"context"
	"errors"
	"fmt"
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
	m.mu.Unlock()
	go func() {
		defer cancel()
		_ = m.refresh(ctx)
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
	m.mu.Unlock()
	return m.refresh(ctx)
}

func (m *Manager) Close() {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.cancel != nil {
		m.cancel()
	}
}

func (m *Manager) refresh(ctx context.Context) error {
	previous := m.snapshot.Load()
	generation := uint64(1)
	if previous != nil {
		generation = previous.Generation + 1
	}
	next, err := build(ctx, m.root, m.vaultName, generation, m.parser)
	now := time.Now().UTC()
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
		return err
	}
	m.snapshot.Store(next)
	m.state = model.IndexState{
		State: "ready", Generation: generation, StartedAt: m.state.StartedAt,
		FinishedAt: now, NoteCount: len(next.Notes), AssetCount: len(next.Assets),
	}
	for _, note := range next.Notes {
		if note.Error != "" {
			m.state.ErrorCount++
		}
	}
	return nil
}
