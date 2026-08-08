package index

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"io/fs"
	"mime"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	mdparser "github.com/markrai/vaultist/server/internal/markdown"
	"github.com/markrai/vaultist/server/internal/model"
	"github.com/markrai/vaultist/server/internal/vault"
)

const maxIndexNoteBytes = 16 << 20

type pendingNote struct {
	relative string
	info     fs.FileInfo
}

func build(ctx context.Context, root, vaultName string, generation uint64, parser *mdparser.Parser) (*Snapshot, error) {
	info, err := os.Stat(root)
	if err != nil || !info.IsDir() {
		return nil, ErrVaultUnavailable
	}
	snapshot := &Snapshot{
		Root: root, VaultName: vaultName, Generation: generation, BuiltAt: time.Now().UTC(),
		AttachmentFolder: readAttachmentFolder(root), Notes: make(map[string]*model.Note),
		Assets: make(map[string]*model.Asset), Backlinks: make(map[string][]model.Backlink),
		Folders:       make(map[string]struct{}),
		notePathExact: make(map[string][]string), notePathFolded: make(map[string][]string),
		noteNameFolded: make(map[string][]string), noteAliasFolded: make(map[string][]string),
		assetPathExact: make(map[string][]string), assetPathFolded: make(map[string][]string),
		assetNameFolded: make(map[string][]string), SearchBlobs: make(map[string]NoteSearchBlobs),
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
		if relErr != nil {
			return ErrIndexBuild
		}
		if relative == "." {
			return nil
		}
		relative = filepath.ToSlash(relative)
		if entry.IsDir() {
			if vault.IsHidden(relative) {
				return fs.SkipDir
			}
			snapshot.Folders[relative] = struct{}{}
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
		if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
			return nil, err
		}
		return nil, ErrIndexBuild
	}
	sort.Slice(notes, func(i, j int) bool { return notes[i].relative < notes[j].relative })
	for _, pending := range notes {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		note, blobs := readAndParseNote(root, pending, parser)
		snapshot.Notes[note.ID] = note
		snapshot.OrderedNoteIDs = append(snapshot.OrderedNoteIDs, note.ID)
		if note.Error == "" {
			snapshot.SearchBlobs[note.ID] = blobs
		}
	}
	sort.Strings(snapshot.OrderedAssetIDs)
	snapshot.buildLookupTables()
	snapshot.resolveAllLinks()
	return snapshot, nil
}

func readAndParseNote(root string, pending pendingNote, parser *mdparser.Parser) (*model.Note, NoteSearchBlobs) {
	id, _ := vault.NoteID(pending.relative)
	note := &model.Note{
		ID: id, Path: pending.relative, Filename: path.Base(pending.relative),
		Title:      strings.TrimSuffix(path.Base(pending.relative), path.Ext(pending.relative)),
		ModifiedAt: pending.info.ModTime().UTC(), Size: pending.info.Size(),
	}
	file, err := vault.OpenFileInside(root, pending.relative)
	if err != nil {
		note.Error = "note could not be read"
		return note, NoteSearchBlobs{}
	}
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, maxIndexNoteBytes+1))
	if err != nil {
		note.Error = "note could not be read"
		return note, NoteSearchBlobs{}
	}
	if len(content) > maxIndexNoteBytes {
		note.Error = "note is too large to index"
		return note, NoteSearchBlobs{}
	}
	hash := sha256.Sum256(content)
	note.Revision = "sha256:" + hex.EncodeToString(hash[:])
	parsed := parser.Parse(content, note.Title)
	note.Title = parsed.Title
	note.Aliases = parsed.Aliases
	note.Headings = parsed.Headings
	note.Links = parsed.Links
	note.Attachments = parsed.Attachments
	return note, NoteSearchBlobs{
		Content: strings.ToLower(string(content)),
		Files:   buildFilesSearchBlob(note),
	}
}

func buildFilesSearchBlob(note *model.Note) string {
	parts := []string{
		strings.ToLower(note.Filename),
		strings.ToLower(note.Title),
	}
	for _, alias := range note.Aliases {
		parts = append(parts, strings.ToLower(alias))
	}
	return strings.Join(parts, "\n")
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
	file, err := vault.OpenFileInside(root, ".obsidian/app.json")
	if err != nil {
		return ""
	}
	defer file.Close()
	data, err := io.ReadAll(io.LimitReader(file, (1<<20)+1))
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
