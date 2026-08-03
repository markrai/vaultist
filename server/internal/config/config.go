package config

import (
	"fmt"
	"net"
	"os"
	"strings"
	"time"
)

type Config struct {
	VaultRoot    string
	VaultName    string
	ListenAddr   string
	ReadTimeout  time.Duration
	WriteTimeout time.Duration
	IdleTimeout  time.Duration
}

func FromEnvironment() (Config, error) {
	config := Config{
		VaultRoot:   strings.TrimSpace(os.Getenv("VAULT_ROOT")),
		VaultName:   strings.TrimSpace(os.Getenv("VAULT_NAME")),
		ListenAddr:  envOrDefault("LISTEN_ADDR", "127.0.0.1:8080"),
		ReadTimeout: 15 * time.Second, WriteTimeout: 60 * time.Second, IdleTimeout: 90 * time.Second,
	}
	if config.VaultRoot == "" {
		return Config{}, fmt.Errorf("VAULT_ROOT is required")
	}
	if _, _, err := net.SplitHostPort(config.ListenAddr); err != nil {
		return Config{}, fmt.Errorf("LISTEN_ADDR must be host:port")
	}
	return config, nil
}

func envOrDefault(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}
