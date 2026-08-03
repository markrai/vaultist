package vault

import (
	"errors"
	"io/fs"
	"path"
	"path/filepath"
	"strings"
)

var ErrInvalidPath = errors.New("invalid vault-relative path")

func NormalizeRelative(value string) (string, error) {
	value = strings.TrimSpace(strings.ReplaceAll(value, "\\", "/"))
	value = strings.TrimPrefix(value, "/")
	if value == "" || strings.Contains(value, "\x00") || strings.Contains(value, ":") {
		return "", ErrInvalidPath
	}
	cleaned := path.Clean(value)
	if cleaned == "." || cleaned == ".." || strings.HasPrefix(cleaned, "../") {
		return "", ErrInvalidPath
	}
	return cleaned, nil
}

func NoteID(relativePath string) (string, error) {
	normalized, err := NormalizeRelative(relativePath)
	if err != nil {
		return "", err
	}
	if !strings.EqualFold(path.Ext(normalized), ".md") {
		return "", ErrInvalidPath
	}
	return strings.TrimSuffix(normalized, path.Ext(normalized)), nil
}

func IsHidden(relativePath string) bool {
	for _, part := range strings.Split(strings.ReplaceAll(relativePath, "\\", "/"), "/") {
		if strings.HasPrefix(part, ".") {
			return true
		}
	}
	return false
}

func JoinInside(root, relativePath string) (string, error) {
	normalized, err := NormalizeRelative(relativePath)
	if err != nil {
		return "", err
	}
	rootAbs, err := filepath.Abs(root)
	if err != nil {
		return "", ErrInvalidPath
	}
	candidate := filepath.Join(rootAbs, filepath.FromSlash(normalized))
	rel, err := filepath.Rel(rootAbs, candidate)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return "", ErrInvalidPath
	}
	if resolved, evalErr := filepath.EvalSymlinks(candidate); evalErr == nil {
		resolvedRel, relErr := filepath.Rel(rootAbs, resolved)
		if relErr != nil || resolvedRel == ".." || strings.HasPrefix(resolvedRel, ".."+string(filepath.Separator)) {
			return "", ErrInvalidPath
		}
		candidate = resolved
	}
	return candidate, nil
}

func Walk(root string, fn fs.WalkDirFunc) error {
	return filepath.WalkDir(root, fn)
}
