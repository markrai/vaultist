package markdown

import (
	"testing"

	"github.com/okayt/vaultist/server/internal/model"
)

func TestParseObsidianAndMarkdownSyntax(t *testing.T) {
	source := []byte(`---
title: "A Better Title"
aliases:
  - Alternate
  - "Unicode Δ"
---
# First Heading

[[Note]] [[Folder/Note|Display]] [[Note#Part|Jump]]
![[image.png]] ![[Note#Preview]]
![Alt](attachments/photo%20one.webp)

` + "`[[not active]]`" + `

~~~text
[[also not active]]
~~~
`)
	parsed := NewParser().Parse(source, "Fallback")
	if parsed.Title != "A Better Title" {
		t.Fatalf("title = %q", parsed.Title)
	}
	if len(parsed.Aliases) != 2 || parsed.Aliases[1] != "Unicode Δ" {
		t.Fatalf("aliases = %#v", parsed.Aliases)
	}
	if len(parsed.Headings) != 1 || parsed.Headings[0].Slug != "first-heading" {
		t.Fatalf("headings = %#v", parsed.Headings)
	}
	if len(parsed.Links) != 6 {
		t.Fatalf("links = %#v", parsed.Links)
	}
	assertLink(t, parsed.Links[0], model.LinkWiki, "Note", "", "", false)
	assertLink(t, parsed.Links[1], model.LinkWiki, "Folder/Note", "", "Display", false)
	assertLink(t, parsed.Links[2], model.LinkWiki, "Note", "Part", "Jump", false)
	assertLink(t, parsed.Links[3], model.LinkWikiEmbed, "image.png", "", "", true)
	assertLink(t, parsed.Links[4], model.LinkWikiEmbed, "Note", "Preview", "", false)
	assertLink(t, parsed.Links[5], model.LinkImage, "attachments/photo one.webp", "", "Alt", true)
	for _, link := range parsed.Links {
		if link.Line < 1 || link.Column < 1 {
			t.Fatalf("position missing: %#v", link)
		}
	}
}

func TestParseFrontmatterScalarAliasAndFallback(t *testing.T) {
	parsed := NewParser().Parse([]byte("---\naliases: single alias\n---\nBody"), "File title")
	if parsed.Title != "File title" {
		t.Fatalf("title = %q", parsed.Title)
	}
	if len(parsed.Aliases) != 1 || parsed.Aliases[0] != "single alias" {
		t.Fatalf("aliases = %#v", parsed.Aliases)
	}
}

func TestStandardMarkdownNoteLink(t *testing.T) {
	parsed := NewParser().Parse([]byte("[Related](../Folder/Related%20Note.md#Details)"), "Source")
	if len(parsed.Links) != 1 {
		t.Fatalf("links = %#v", parsed.Links)
	}
	link := parsed.Links[0]
	if link.Target != "../Folder/Related Note.md" || link.Fragment != "Details" || link.IsAsset {
		t.Fatalf("link = %#v", link)
	}
}

func TestWikiLinkDecodesUnicodeAndSpaces(t *testing.T) {
	parsed := NewParser().Parse([]byte("[[Unicode/%C3%9Cber%20Note|Open]]"), "Source")
	if len(parsed.Links) != 1 || parsed.Links[0].Target != "Unicode/Über Note" || parsed.Links[0].Display != "Open" {
		t.Fatalf("links = %#v", parsed.Links)
	}
}

func assertLink(t *testing.T, link model.LinkOccurrence, kind model.LinkKind, target, fragment, display string, asset bool) {
	t.Helper()
	if link.Kind != kind || link.Target != target || link.Fragment != fragment || link.Display != display || link.IsAsset != asset {
		t.Fatalf("link = %#v", link)
	}
}
