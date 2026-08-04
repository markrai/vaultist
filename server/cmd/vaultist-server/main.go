package main

import (
	"context"
	"errors"
	"log"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/markrai/vaultist/server/internal/api"
	"github.com/markrai/vaultist/server/internal/config"
	"github.com/markrai/vaultist/server/internal/index"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))
	if len(os.Args) == 2 && os.Args[1] == "--healthcheck" {
		healthcheck()
		return
	}
	configuration, err := config.FromEnvironment()
	if err != nil {
		log.Fatalf("configuration error: %v", err)
	}
	manager, err := index.NewManager(configuration.VaultRoot, configuration.VaultName)
	if err != nil {
		log.Fatalf("index configuration error: %v", err)
	}
	defer manager.Close()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if err := manager.StartRefresh(ctx); err != nil {
		log.Printf("initial index could not start")
	}
	server := &http.Server{
		Addr: configuration.ListenAddr, Handler: api.NewHandler(manager, api.TailnetAuthorizer{}),
		ReadHeaderTimeout: 5 * time.Second, ReadTimeout: configuration.ReadTimeout,
		WriteTimeout: configuration.WriteTimeout, IdleTimeout: configuration.IdleTimeout,
		MaxHeaderBytes: 1 << 20,
	}
	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := server.Shutdown(shutdownCtx); err != nil {
			log.Printf("shutdown did not complete cleanly")
		}
	}()
	log.Printf("Vaultist listening on %s", configuration.ListenAddr)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("server stopped: %v", err)
	}
}

func healthcheck() {
	endpoint := os.Getenv("VAULTIST_HEALTH_URL")
	if endpoint == "" {
		endpoint = "http://127.0.0.1:8080/api/v1/status"
	}
	client := &http.Client{Timeout: 2 * time.Second}
	response, err := client.Get(endpoint)
	if err != nil {
		os.Exit(1)
	}
	_ = response.Body.Close()
	if response.StatusCode != http.StatusOK {
		os.Exit(1)
	}
}
