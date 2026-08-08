package vault

import (
	"os"
)

// OpenFileInside opens a regular filesystem path through os.Root so concurrent
// symlink changes cannot redirect access outside the canonical vault root.
func OpenFileInside(root, relativePath string) (*os.File, error) {
	normalized, err := NormalizeRelative(relativePath)
	if err != nil {
		return nil, err
	}
	_, rootHandle, err := openRoot(root)
	if err != nil {
		return nil, err
	}
	defer rootHandle.Close()
	if err := rejectSymlinkComponents(rootHandle, normalized, false); err != nil {
		return nil, err
	}
	return rootHandle.Open(normalized)
}

func StatInside(root, relativePath string) (os.FileInfo, error) {
	file, err := OpenFileInside(root, relativePath)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	return file.Stat()
}
