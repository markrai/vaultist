package index

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
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
