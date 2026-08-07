package vault

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
)

func ReplaceFileAtomically(root, relativePath string, content []byte) error {
	target, err := JoinInside(root, relativePath)
	if err != nil {
		return err
	}
	dir := filepath.Dir(target)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return fmt.Errorf("create note directory: %w", err)
	}
	temp, err := os.CreateTemp(dir, ".vaultist-*")
	if err != nil {
		return fmt.Errorf("create temp file: %w", err)
	}
	tempPath := temp.Name()
	cleanup := true
	defer func() {
		if cleanup {
			_ = os.Remove(tempPath)
		}
	}()
	if _, err := temp.Write(content); err != nil {
		_ = temp.Close()
		return fmt.Errorf("write temp file: %w", err)
	}
	if err := temp.Sync(); err != nil {
		_ = temp.Close()
		return fmt.Errorf("sync temp file: %w", err)
	}
	if err := temp.Close(); err != nil {
		return fmt.Errorf("close temp file: %w", err)
	}
	if err := os.Rename(tempPath, target); err != nil {
		return fmt.Errorf("replace note file: %w", err)
	}
	cleanup = false
	if dirFile, err := os.Open(dir); err == nil {
		_ = dirFile.Sync()
		_ = dirFile.Close()
	}
	return nil
}

func DeleteFileInside(root, relativePath string) error {
	target, err := JoinInside(root, relativePath)
	if err != nil {
		return err
	}
	if err := os.Remove(target); err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("note file not found: %w", err)
		}
		return fmt.Errorf("delete note file: %w", err)
	}
	dir := filepath.Dir(target)
	if dirFile, err := os.Open(dir); err == nil {
		_ = dirFile.Sync()
		_ = dirFile.Close()
	}
	return nil
}

var ErrFolderExists = errors.New("folder already exists")
var ErrFolderNotEmpty = errors.New("folder not empty")
var ErrPathOccupied = errors.New("path occupied by file")

func MkdirInside(root, relativePath string) error {
	target, err := JoinInside(root, relativePath)
	if err != nil {
		return err
	}
	if info, statErr := os.Stat(target); statErr == nil {
		if info.IsDir() {
			return ErrFolderExists
		}
		return ErrPathOccupied
	} else if !os.IsNotExist(statErr) {
		return fmt.Errorf("create folder: %w", statErr)
	}
	if err := os.MkdirAll(target, 0o755); err != nil {
		return fmt.Errorf("create folder: %w", err)
	}
	dir := filepath.Dir(target)
	if dirFile, err := os.Open(dir); err == nil {
		_ = dirFile.Sync()
		_ = dirFile.Close()
	}
	return nil
}

func RemoveDirInside(root, relativePath string) error {
	target, err := JoinInside(root, relativePath)
	if err != nil {
		return err
	}
	info, statErr := os.Stat(target)
	if statErr != nil {
		if os.IsNotExist(statErr) {
			return fmt.Errorf("folder not found: %w", statErr)
		}
		return fmt.Errorf("delete folder: %w", statErr)
	}
	if !info.IsDir() {
		return ErrPathOccupied
	}
	entries, err := os.ReadDir(target)
	if err != nil {
		return fmt.Errorf("delete folder: %w", err)
	}
	if len(entries) > 0 {
		return ErrFolderNotEmpty
	}
	if err := os.Remove(target); err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("folder not found: %w", err)
		}
		return fmt.Errorf("delete folder: %w", err)
	}
	dir := filepath.Dir(target)
	if dirFile, err := os.Open(dir); err == nil {
		_ = dirFile.Sync()
		_ = dirFile.Close()
	}
	return nil
}
