package index

import (
	"path"
	"sort"
	"strings"
	"time"

	"github.com/markrai/vaultist/server/internal/model"
)

type Snapshot struct {
	Root             string
	VaultName        string
	Generation       uint64
	BuiltAt          time.Time
	AttachmentFolder string
	Notes            map[string]*model.Note
	Assets           map[string]*model.Asset
	Backlinks        map[string][]model.Backlink
	Folders          map[string]struct{}
	OrderedNoteIDs   []string
	OrderedAssetIDs  []string
	notePathExact    map[string][]string
	notePathFolded   map[string][]string
	noteNameFolded   map[string][]string
	noteAliasFolded  map[string][]string
	assetPathExact   map[string][]string
	assetPathFolded  map[string][]string
	assetNameFolded  map[string][]string
	// SearchBlobs maps note ID to precomputed lowercase searchable text built at index time.
	SearchBlobs map[string]NoteSearchBlobs
}

// NoteSearchBlobs holds index-time search text for files and content modes.
type NoteSearchBlobs struct {
	Content string
	Files   string
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
