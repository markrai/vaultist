package index

import (
	"path"
	"sort"
	"strings"

	"github.com/markrai/vaultist/server/internal/model"
	"github.com/markrai/vaultist/server/internal/vault"
)

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
