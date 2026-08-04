package index

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"mime"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	mdparser "github.com/okayt/vaultist/server/internal/markdown"
	"github.com/okayt/vaultist/server/internal/model"
	"github.com/okayt/vaultist/server/internal/vault"
)

var (
	ErrNotReady      = errors.New("index not ready")
	ErrRefreshActive = errors.New("index refresh already running")
	ErrNotFound      = errors.New("not found")
)

const maxIndexNoteBytes = 16 << 20

type Snapshot struct {
	Root             string
	VaultName        string
	Generation       uint64
	BuiltAt          time.Time
	AttachmentFolder string
	Notes            map[string]*model.Note
	Assets           map[string]*model.Asset
	Backlinks        map[string][]model.Backlink
	OrderedNoteIDs   []string
	OrderedAssetIDs  []string
	notePathExact    map[string][]string
	notePathFolded   map[string][]string
	noteNameFolded   map[string][]string
	noteAliasFolded  map[string][]string
	assetPathExact   map[string][]string
	assetPathFolded  map[string][]string
	assetNameFolded  map[string][]string
}

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

type pendingNote struct {
	relative string
	info     fs.FileInfo
}

func build(ctx context.Context, root, vaultName string, generation uint64, parser *mdparser.Parser) (*Snapshot, error) {
	info, err := os.Stat(root)
	if err != nil || !info.IsDir() {
		return nil, fmt.Errorf("vault unavailable")
	}
	snapshot := &Snapshot{
		Root: root, VaultName: vaultName, Generation: generation, BuiltAt: time.Now().UTC(),
		AttachmentFolder: readAttachmentFolder(root), Notes: make(map[string]*model.Note),
		Assets: make(map[string]*model.Asset), Backlinks: make(map[string][]model.Backlink),
		notePathExact: make(map[string][]string), notePathFolded: make(map[string][]string),
		noteNameFolded: make(map[string][]string), noteAliasFolded: make(map[string][]string),
		assetPathExact: make(map[string][]string), assetPathFolded: make(map[string][]string),
		assetNameFolded: make(map[string][]string),
	}
	var notes []pendingNote
	err = vault.Walk(root, func(filePath string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			if entry != nil && entry.IsDir() {
				return fs.SkipDir
			}
			return nil
		}
		if err := ctx.Err(); err != nil {
			return err
		}
		relative, relErr := filepath.Rel(root, filePath)
		if relErr != nil || relative == "." {
			return relErr
		}
		relative = filepath.ToSlash(relative)
		if entry.IsDir() {
			if vault.IsHidden(relative) {
				return fs.SkipDir
			}
			return nil
		}
		if vault.IsHidden(relative) || entry.Type()&os.ModeSymlink != 0 {
			return nil
		}
		fileInfo, infoErr := entry.Info()
		if infoErr != nil || !fileInfo.Mode().IsRegular() {
			return nil
		}
		extension := strings.ToLower(path.Ext(relative))
		if extension == ".md" {
			notes = append(notes, pendingNote{relative: relative, info: fileInfo})
		} else if mediaTypeForExtension(extension) != "" {
			asset := makeAsset(relative, fileInfo)
			snapshot.Assets[asset.ID] = asset
			snapshot.OrderedAssetIDs = append(snapshot.OrderedAssetIDs, asset.ID)
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	sort.Slice(notes, func(i, j int) bool { return notes[i].relative < notes[j].relative })
	for _, pending := range notes {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		note := readAndParseNote(root, pending, parser)
		snapshot.Notes[note.ID] = note
		snapshot.OrderedNoteIDs = append(snapshot.OrderedNoteIDs, note.ID)
	}
	sort.Strings(snapshot.OrderedAssetIDs)
	snapshot.buildLookupTables()
	snapshot.resolveAllLinks()
	return snapshot, nil
}

func readAndParseNote(root string, pending pendingNote, parser *mdparser.Parser) *model.Note {
	id, _ := vault.NoteID(pending.relative)
	note := &model.Note{
		ID: id, Path: pending.relative, Filename: path.Base(pending.relative),
		Title:      strings.TrimSuffix(path.Base(pending.relative), path.Ext(pending.relative)),
		ModifiedAt: pending.info.ModTime().UTC(), Size: pending.info.Size(),
	}
	filePath, err := vault.JoinInside(root, pending.relative)
	if err != nil {
		note.Error = "invalid note path"
		return note
	}
	file, err := os.Open(filePath)
	if err != nil {
		note.Error = "note could not be read"
		return note
	}
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, maxIndexNoteBytes+1))
	if err != nil {
		note.Error = "note could not be read"
		return note
	}
	if len(content) > maxIndexNoteBytes {
		note.Error = "note is too large to index"
		return note
	}
	hash := sha256.Sum256(content)
	note.Revision = "sha256:" + hex.EncodeToString(hash[:])
	parsed := parser.Parse(content, note.Title)
	note.Title = parsed.Title
	note.Aliases = parsed.Aliases
	note.Headings = parsed.Headings
	note.Links = parsed.Links
	note.Attachments = parsed.Attachments
	return note
}

func makeAsset(relative string, info fs.FileInfo) *model.Asset {
	mediaType := mediaTypeForExtension(strings.ToLower(path.Ext(relative)))
	return &model.Asset{
		ID: relative, Path: relative, Filename: path.Base(relative), MediaType: mediaType,
		ModifiedAt: info.ModTime().UTC(), Size: info.Size(),
		ETag: `W/"` + strconv.FormatInt(info.Size(), 16) + `-` + strconv.FormatInt(info.ModTime().UnixNano(), 16) + `"`,
	}
}

func mediaTypeForExtension(extension string) string {
	switch strings.ToLower(extension) {
	case ".png":
		return "image/png"
	case ".jpg", ".jpeg":
		return "image/jpeg"
	case ".webp":
		return "image/webp"
	case ".gif":
		return "image/gif"
	case ".svg":
		return "image/svg+xml"
	default:
		if detected := mime.TypeByExtension(extension); strings.HasPrefix(detected, "image/") {
			return detected
		}
		return ""
	}
}

func readAttachmentFolder(root string) string {
	configPath := filepath.Join(root, ".obsidian", "app.json")
	data, err := os.ReadFile(configPath)
	if err != nil || len(data) > 1<<20 {
		return ""
	}
	var config struct {
		AttachmentFolder string `json:"attachmentFolderPath"`
	}
	if json.Unmarshal(data, &config) != nil {
		return ""
	}
	folder := strings.TrimSpace(strings.ReplaceAll(config.AttachmentFolder, "\\", "/"))
	if folder == "" || folder == "." || folder == "./" {
		return ""
	}
	if normalized, err := vault.NormalizeRelative(folder); err == nil {
		return normalized
	}
	return ""
}

func (s *Snapshot) buildLookupTables() {
	for id, note := range s.Notes {
		withoutExtension := strings.TrimSuffix(note.Path, path.Ext(note.Path))
		addLookup(s.notePathExact, withoutExtension, id)
		addLookup(s.notePathExact, note.Path, id)
		addLookup(s.notePathFolded, strings.ToLower(withoutExtension), id)
		addLookup(s.notePathFolded, strings.ToLower(note.Path), id)
		name := strings.TrimSuffix(note.Filename, path.Ext(note.Filename))
		addLookup(s.noteNameFolded, strings.ToLower(name), id)
		for _, alias := range note.Aliases {
			addLookup(s.noteAliasFolded, strings.ToLower(strings.TrimSpace(alias)), id)
		}
	}
	for id, asset := range s.Assets {
		addLookup(s.assetPathExact, asset.Path, id)
		addLookup(s.assetPathFolded, strings.ToLower(asset.Path), id)
		addLookup(s.assetNameFolded, strings.ToLower(asset.Filename), id)
	}
	for _, table := range []map[string][]string{
		s.notePathExact, s.notePathFolded, s.noteNameFolded, s.noteAliasFolded,
		s.assetPathExact, s.assetPathFolded, s.assetNameFolded,
	} {
		for key := range table {
			sort.Strings(table[key])
		}
	}
}

func addLookup(table map[string][]string, key, id string) {
	if key == "" {
		return
	}
	for _, existing := range table[key] {
		if existing == id {
			return
		}
	}
	table[key] = append(table[key], id)
}

func (s *Snapshot) resolveAllLinks() {
	for _, id := range s.OrderedNoteIDs {
		note := s.Notes[id]
		for linkIndex := range note.Links {
			link := &note.Links[linkIndex]
			if link.IsAsset {
				link.Resolution = s.ResolveAsset(note.Path, link.Target)
			} else {
				link.Resolution = s.ResolveNote(note.Path, link.Target, link.Kind == model.LinkMarkdown)
				if link.Resolution.Status == model.LinkResolved && (link.Kind != model.LinkWikiEmbed || !link.IsAsset) {
					s.Backlinks[link.Resolution.NoteID] = append(s.Backlinks[link.Resolution.NoteID], model.Backlink{
						SourceID: note.ID, SourceTitle: note.Title, SourcePath: note.Path,
						Line: link.Line, Column: link.Column, Context: link.Context,
						Fragment: link.Fragment, Display: link.Display, OccurrenceKind: link.Kind,
					})
				}
			}
		}
	}
	for id := range s.Backlinks {
		sort.SliceStable(s.Backlinks[id], func(i, j int) bool {
			a, b := s.Backlinks[id][i], s.Backlinks[id][j]
			if a.SourcePath != b.SourcePath {
				return a.SourcePath < b.SourcePath
			}
			if a.Line != b.Line {
				return a.Line < b.Line
			}
			return a.Column < b.Column
		})
	}
}

func (s *Snapshot) ResolveNote(sourcePath, target string, preferRelative bool) model.Resolution {
	target = strings.TrimSpace(strings.ReplaceAll(target, "\\", "/"))
	if target == "" {
		if source, ok := s.noteByPath(sourcePath); ok {
			return model.Resolution{Status: model.LinkResolved, NoteID: source.ID}
		}
		return model.Resolution{Status: model.LinkMissing}
	}
	target = trimMarkdownExtension(target)
	var candidateIDs []string
	sourceDir := path.Dir(sourcePath)
	if sourceDir == "." {
		sourceDir = ""
	}
	if (preferRelative || strings.HasPrefix(target, ".")) && !strings.HasPrefix(target, "/") {
		if relative, ok := validCandidatePath(path.Join(sourceDir, target)); ok {
			candidateIDs = append(candidateIDs, s.lookupNotePath(relative)...)
		}
	}
	if rootRelative, ok := validCandidatePath(strings.TrimPrefix(target, "/")); ok {
		candidateIDs = append(candidateIDs, s.lookupNotePath(rootRelative)...)
	}
	if !strings.Contains(target, "/") {
		candidateIDs = append(candidateIDs, s.noteNameFolded[strings.ToLower(target)]...)
		candidateIDs = append(candidateIDs, s.noteAliasFolded[strings.ToLower(target)]...)
	}
	return s.noteResolution(uniqueSorted(candidateIDs))
}

func (s *Snapshot) ResolveAsset(sourcePath, target string) model.Resolution {
	target = strings.TrimSpace(strings.ReplaceAll(target, "\\", "/"))
	if target == "" {
		return model.Resolution{Status: model.LinkMissing}
	}
	sourceDir := path.Dir(sourcePath)
	if sourceDir == "." {
		sourceDir = ""
	}
	var ids []string
	if !strings.HasPrefix(target, "/") {
		if relative, ok := validCandidatePath(path.Join(sourceDir, target)); ok {
			ids = append(ids, s.lookupAssetPath(relative)...)
		}
	}
	if rootRelative, ok := validCandidatePath(strings.TrimPrefix(target, "/")); ok {
		ids = append(ids, s.lookupAssetPath(rootRelative)...)
	}
	if s.AttachmentFolder != "" {
		if attachmentRelative, ok := validCandidatePath(path.Join(s.AttachmentFolder, target)); ok {
			ids = append(ids, s.lookupAssetPath(attachmentRelative)...)
		}
	}
	if !strings.Contains(target, "/") {
		ids = append(ids, s.assetNameFolded[strings.ToLower(path.Base(target))]...)
	}
	ids = uniqueSorted(ids)
	if len(ids) == 1 {
		return model.Resolution{Status: model.LinkResolved, AssetID: ids[0]}
	}
	if len(ids) > 1 {
		candidates := make([]model.Candidate, 0, len(ids))
		for _, id := range ids {
			asset := s.Assets[id]
			candidates = append(candidates, model.Candidate{ID: id, Title: asset.Filename, Path: asset.Path})
		}
		return model.Resolution{Status: model.LinkAmbiguous, Candidates: candidates}
	}
	return model.Resolution{Status: model.LinkMissing}
}

func validCandidatePath(candidate string) (string, bool) {
	normalized, err := vault.NormalizeRelative(candidate)
	return normalized, err == nil
}

func (s *Snapshot) noteByPath(notePath string) (*model.Note, bool) {
	ids := s.notePathExact[notePath]
	if len(ids) == 0 {
		ids = s.notePathExact[strings.TrimSuffix(notePath, path.Ext(notePath))]
	}
	if len(ids) != 1 {
		return nil, false
	}
	note, ok := s.Notes[ids[0]]
	return note, ok
}

func (s *Snapshot) lookupNotePath(target string) []string {
	target = trimMarkdownExtension(target)
	if ids := s.notePathExact[target]; len(ids) > 0 {
		return ids
	}
	return s.notePathFolded[strings.ToLower(target)]
}

func trimMarkdownExtension(value string) string {
	extension := path.Ext(value)
	if strings.EqualFold(extension, ".md") {
		return strings.TrimSuffix(value, extension)
	}
	return value
}

func (s *Snapshot) lookupAssetPath(target string) []string {
	if ids := s.assetPathExact[target]; len(ids) > 0 {
		return ids
	}
	return s.assetPathFolded[strings.ToLower(target)]
}

func (s *Snapshot) noteResolution(ids []string) model.Resolution {
	if len(ids) == 1 {
		return model.Resolution{Status: model.LinkResolved, NoteID: ids[0]}
	}
	if len(ids) == 0 {
		return model.Resolution{Status: model.LinkMissing}
	}
	candidates := make([]model.Candidate, 0, len(ids))
	for _, id := range ids {
		note := s.Notes[id]
		candidates = append(candidates, model.Candidate{ID: id, Title: note.Title, Path: note.Path})
	}
	return model.Resolution{Status: model.LinkAmbiguous, Candidates: candidates}
}

func uniqueSorted(values []string) []string {
	set := make(map[string]struct{}, len(values))
	for _, value := range values {
		set[value] = struct{}{}
	}
	result := make([]string, 0, len(set))
	for value := range set {
		result = append(result, value)
	}
	sort.Strings(result)
	return result
}

func (s *Snapshot) OpenNote(id string) (*model.Note, *os.File, error) {
	note, ok := s.Notes[id]
	if !ok {
		return nil, nil, ErrNotFound
	}
	filePath, err := vault.JoinInside(s.Root, note.Path)
	if err != nil {
		return nil, nil, ErrNotFound
	}
	file, err := os.Open(filePath)
	if err != nil {
		return nil, nil, err
	}
	return note, file, nil
}

func (s *Snapshot) OpenAsset(id string) (*model.Asset, *os.File, error) {
	asset, ok := s.Assets[id]
	if !ok {
		return nil, nil, ErrNotFound
	}
	filePath, err := vault.JoinInside(s.Root, asset.Path)
	if err != nil {
		return nil, nil, ErrNotFound
	}
	file, err := os.Open(filePath)
	if err != nil {
		return nil, nil, err
	}
	return asset, file, nil
}
