package index

import (
	"context"
	"errors"
)

var (
	ErrVaultUnavailable = errors.New("vault unavailable")
	ErrIndexBuild       = errors.New("index build failed")
)

func classifyRefreshError(err error) string {
	if err == nil {
		return ""
	}
	switch {
	case errors.Is(err, context.Canceled):
		return "canceled"
	case errors.Is(err, context.DeadlineExceeded):
		return "deadline_exceeded"
	case errors.Is(err, ErrVaultUnavailable):
		return "vault_unavailable"
	default:
		return "build_failed"
	}
}
