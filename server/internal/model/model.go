package model

import "time"

type LinkKind string

const (
	LinkWiki      LinkKind = "wiki"
	LinkWikiEmbed LinkKind = "wiki_embed"
	LinkMarkdown  LinkKind = "markdown"
	LinkImage     LinkKind = "image"
)

type LinkStatus string

const (
	LinkResolved  LinkStatus = "resolved"
	LinkMissing   LinkStatus = "missing"
	LinkAmbiguous LinkStatus = "ambiguous"
	LinkExternal  LinkStatus = "external"
)

type Heading struct {
	Level int    `json:"level"`
	Text  string `json:"text"`
	Slug  string `json:"slug"`
}

type LinkOccurrence struct {
	Kind       LinkKind   `json:"kind"`
	Raw        string     `json:"raw"`
	Target     string     `json:"target"`
	Fragment   string     `json:"fragment,omitempty"`
	Display    string     `json:"display,omitempty"`
	Line       int        `json:"line"`
	Column     int        `json:"column"`
	Context    string     `json:"context,omitempty"`
	IsEmbed    bool       `json:"isEmbed"`
	IsAsset    bool       `json:"isAsset"`
	Resolution Resolution `json:"resolution"`
}

type Resolution struct {
	Status     LinkStatus  `json:"status"`
	NoteID     string      `json:"noteId,omitempty"`
	AssetID    string      `json:"assetId,omitempty"`
	Candidates []Candidate `json:"candidates,omitempty"`
}

type Candidate struct {
	ID    string `json:"id"`
	Title string `json:"title"`
	Path  string `json:"path"`
}

type ParsedNote struct {
	Title       string
	Aliases     []string
	Headings    []Heading
	Links       []LinkOccurrence
	Attachments []string
}

type Note struct {
	ID          string           `json:"id"`
	Path        string           `json:"path"`
	Filename    string           `json:"filename"`
	Title       string           `json:"title"`
	Aliases     []string         `json:"aliases"`
	Headings    []Heading        `json:"headings"`
	Links       []LinkOccurrence `json:"links"`
	Attachments []string         `json:"attachments"`
	ModifiedAt  time.Time        `json:"modifiedAt"`
	Size        int64            `json:"size"`
	Revision    string           `json:"revision"`
	Error       string           `json:"error,omitempty"`
}

type Asset struct {
	ID         string    `json:"id"`
	Path       string    `json:"path"`
	Filename   string    `json:"filename"`
	MediaType  string    `json:"mediaType"`
	ModifiedAt time.Time `json:"modifiedAt"`
	Size       int64     `json:"size"`
	ETag       string    `json:"etag"`
}

type Backlink struct {
	SourceID       string   `json:"sourceId"`
	SourceTitle    string   `json:"sourceTitle"`
	SourcePath     string   `json:"sourcePath"`
	Line           int      `json:"line"`
	Column         int      `json:"column"`
	Context        string   `json:"context"`
	Fragment       string   `json:"fragment,omitempty"`
	Display        string   `json:"display,omitempty"`
	OccurrenceKind LinkKind `json:"occurrenceKind"`
}

type IndexState struct {
	State      string    `json:"state"`
	Generation uint64    `json:"generation"`
	StartedAt  time.Time `json:"startedAt,omitempty"`
	FinishedAt time.Time `json:"finishedAt,omitempty"`
	NoteCount  int       `json:"noteCount"`
	AssetCount int       `json:"assetCount"`
	ErrorCount int       `json:"errorCount"`
}
