package vault

import (
	"errors"
	"io/fs"
	"os"
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
	if IsHidden(normalized) {
		return "", ErrInvalidPath
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

func openRoot(root string) (string, *os.Root, error) {
	rootAbs, err := filepath.Abs(root)
	if err != nil {
		return "", nil, err
	}
	rootAbs, err = filepath.EvalSymlinks(rootAbs)
	if err != nil {
		return "", nil, err
	}
	handle, err := os.OpenRoot(rootAbs)
	if err != nil {
		return "", nil, err
	}
	return rootAbs, handle, nil
}

func rejectSymlinkComponents(root *os.Root, normalized string, allowMissing bool) error {
	// Rejecting stable symlink components is Vaultist policy. Rooted operations,
	// not this pre-check, enforce confinement during concurrent replacement.
	var current string
	for _, component := range strings.Split(normalized, "/") {
		current = path.Join(current, component)
		info, err := root.Lstat(filepath.FromSlash(current))
		if err != nil {
			if allowMissing && os.IsNotExist(err) {
				return nil
			}
			return err
		}
		if info.Mode()&os.ModeSymlink != 0 {
			return ErrInvalidPath
		}
	}
	return nil
}

func Walk(root string, fn fs.WalkDirFunc) error {
	rootAbs, rootHandle, err := openRoot(root)
	if err != nil {
		return err
	}
	defer rootHandle.Close()
	return fs.WalkDir(rootHandle.FS(), ".", func(relative string, entry fs.DirEntry, walkErr error) error {
		filePath := filepath.Join(rootAbs, filepath.FromSlash(relative))
		return fn(filePath, entry, walkErr)
	})
}
