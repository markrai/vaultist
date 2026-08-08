package vault

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path"
)

func ReplaceFileAtomically(root, relativePath string, content []byte) error {
	normalized, rootHandle, err := writableRoot(root, relativePath)
	if err != nil {
		return err
	}
	defer rootHandle.Close()
	dir := path.Dir(normalized)
	if err := ensureDirectory(rootHandle, dir); err != nil {
		return fmt.Errorf("create note directory: %w", err)
	}
	if err := rejectSymlinkComponents(rootHandle, normalized, true); err != nil {
		return err
	}
	tempPath, temp, err := createTempFile(rootHandle, dir)
	if err != nil {
		return fmt.Errorf("create temp file: %w", err)
	}
	cleanup := true
	defer func() {
		if cleanup {
			_ = rootHandle.Remove(tempPath)
		}
	}()
	if err := writeAndCloseTemp(temp, content); err != nil {
		return err
	}
	if err := rejectSymlinkComponents(rootHandle, normalized, true); err != nil {
		return err
	}
	if err := rootHandle.Rename(tempPath, normalized); err != nil {
		return fmt.Errorf("replace note file: %w", err)
	}
	cleanup = false
	syncDirectory(rootHandle, dir)
	return nil
}

func CreateFileAtomically(root, relativePath string, content []byte) error {
	normalized, rootHandle, err := writableRoot(root, relativePath)
	if err != nil {
		return err
	}
	defer rootHandle.Close()
	dir := path.Dir(normalized)
	if err := ensureDirectory(rootHandle, dir); err != nil {
		return fmt.Errorf("create note directory: %w", err)
	}
	if err := rejectSymlinkComponents(rootHandle, normalized, true); err != nil {
		return err
	}
	tempPath, temp, err := createTempFile(rootHandle, dir)
	if err != nil {
		return fmt.Errorf("create temp file: %w", err)
	}
	defer rootHandle.Remove(tempPath)
	if err := writeAndCloseTemp(temp, content); err != nil {
		return err
	}
	if err := rejectSymlinkComponents(rootHandle, normalized, true); err != nil {
		return err
	}
	if err := rootHandle.Link(tempPath, normalized); err != nil {
		if os.IsExist(err) {
			return ErrPathOccupied
		}
		return fmt.Errorf("create note file: %w", err)
	}
	if err := rootHandle.Remove(tempPath); err != nil {
		return fmt.Errorf("remove temp file: %w", err)
	}
	syncDirectory(rootHandle, dir)
	return nil
}

func DeleteFileInside(root, relativePath string) error {
	normalized, rootHandle, err := writableRoot(root, relativePath)
	if err != nil {
		return err
	}
	defer rootHandle.Close()
	if err := rejectSymlinkComponents(rootHandle, normalized, false); err != nil {
		return err
	}
	if err := rootHandle.Remove(normalized); err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("note file not found: %w", err)
		}
		return fmt.Errorf("delete note file: %w", err)
	}
	syncDirectory(rootHandle, path.Dir(normalized))
	return nil
}

var ErrFolderExists = errors.New("folder already exists")
var ErrFolderNotEmpty = errors.New("folder not empty")
var ErrPathOccupied = errors.New("path occupied by file")

func MkdirInside(root, relativePath string) error {
	normalized, rootHandle, err := writableRoot(root, relativePath)
	if err != nil {
		return err
	}
	defer rootHandle.Close()
	if info, statErr := rootHandle.Lstat(normalized); statErr == nil {
		if info.Mode()&os.ModeSymlink != 0 {
			return ErrInvalidPath
		}
		if info.IsDir() {
			return ErrFolderExists
		}
		return ErrPathOccupied
	} else if !os.IsNotExist(statErr) {
		return fmt.Errorf("create folder: %w", statErr)
	}
	parent := path.Dir(normalized)
	if err := ensureDirectory(rootHandle, parent); err != nil {
		return fmt.Errorf("create folder: %w", err)
	}
	if err := rejectSymlinkComponents(rootHandle, normalized, true); err != nil {
		return err
	}
	if err := rootHandle.Mkdir(normalized, 0o755); err != nil {
		if os.IsExist(err) {
			return ErrFolderExists
		}
		return fmt.Errorf("create folder: %w", err)
	}
	syncDirectory(rootHandle, parent)
	return nil
}

func RemoveDirInside(root, relativePath string) error {
	normalized, rootHandle, err := writableRoot(root, relativePath)
	if err != nil {
		return err
	}
	defer rootHandle.Close()
	if err := rejectSymlinkComponents(rootHandle, normalized, false); err != nil {
		return err
	}
	dirHandle, statErr := rootHandle.Open(normalized)
	if statErr != nil {
		if os.IsNotExist(statErr) {
			return fmt.Errorf("folder not found: %w", statErr)
		}
		return fmt.Errorf("delete folder: %w", statErr)
	}
	defer dirHandle.Close()
	info, statErr := dirHandle.Stat()
	if statErr != nil {
		return fmt.Errorf("delete folder: %w", statErr)
	}
	if !info.IsDir() {
		return ErrPathOccupied
	}
	entries, err := dirHandle.ReadDir(1)
	if err != nil && !errors.Is(err, io.EOF) {
		return fmt.Errorf("delete folder: %w", err)
	}
	if len(entries) > 0 {
		return ErrFolderNotEmpty
	}
	if err := dirHandle.Close(); err != nil {
		return fmt.Errorf("delete folder: %w", err)
	}
	if err := rootHandle.Remove(normalized); err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("folder not found: %w", err)
		}
		return fmt.Errorf("delete folder: %w", err)
	}
	syncDirectory(rootHandle, path.Dir(normalized))
	return nil
}

func writableRoot(root, relativePath string) (string, *os.Root, error) {
	normalized, err := NormalizeRelative(relativePath)
	if err != nil {
		return "", nil, err
	}
	_, rootHandle, err := openRoot(root)
	if err != nil {
		return "", nil, err
	}
	return normalized, rootHandle, nil
}

func ensureDirectory(root *os.Root, relativePath string) error {
	if relativePath == "." {
		return nil
	}
	if err := rejectSymlinkComponents(root, relativePath, true); err != nil {
		return err
	}
	if err := root.MkdirAll(relativePath, 0o755); err != nil {
		return err
	}
	return rejectSymlinkComponents(root, relativePath, false)
}

func createTempFile(root *os.Root, dir string) (string, *os.File, error) {
	for range 100 {
		random := make([]byte, 16)
		if _, err := rand.Read(random); err != nil {
			return "", nil, err
		}
		name := path.Join(dir, ".vaultist-"+hex.EncodeToString(random))
		file, err := root.OpenFile(name, os.O_RDWR|os.O_CREATE|os.O_EXCL, 0o600)
		if err == nil {
			return name, file, nil
		}
		if !os.IsExist(err) {
			return "", nil, err
		}
	}
	return "", nil, fmt.Errorf("could not allocate temp file")
}

func writeAndCloseTemp(temp *os.File, content []byte) error {
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
	return nil
}

func syncDirectory(root *os.Root, relativePath string) {
	dir, err := root.Open(relativePath)
	if err != nil {
		return
	}
	_ = dir.Sync()
	_ = dir.Close()
}
