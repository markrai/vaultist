# syntax=docker/dockerfile:1.7
FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/vaultview-server ./cmd/vaultview-server

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/vaultview-server /vaultview-server
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/vaultview-server"]
