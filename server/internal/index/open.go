package index

import (
	"os"

	"github.com/markrai/vaultist/server/internal/model"
	"github.com/markrai/vaultist/server/internal/vault"
)

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
