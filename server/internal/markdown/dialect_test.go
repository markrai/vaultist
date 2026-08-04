package markdown

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/markrai/vaultist/server/internal/model"
)

func fixtureDir(t *testing.T) string {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("runtime.Caller failed")
	}
	return filepath.Clean(filepath.Join(filepath.Dir(file), "..", "..", "..", "fixtures", "markdown"))
}

func fixturePath(t *testing.T, name string) string {
	t.Helper()
	return filepath.Join(fixtureDir(t), name)
}

type dialectExpected struct {
	Title    string            `json:"title"`
	Aliases  []string          `json:"aliases"`
	Headings []headingExpected `json:"headings"`
	Links    []linkExpected    `json:"links"`
}

type headingExpected struct {
	Level int    `json:"level"`
	Text  string `json:"text"`
	Slug  string `json:"slug"`
}

type linkExpected struct {
	Kind     string `json:"kind"`
	Target   string `json:"target"`
	Fragment string `json:"fragment,omitempty"`
	Display  string `json:"display,omitempty"`
	IsEmbed  bool   `json:"isEmbed"`
	IsAsset  bool   `json:"isAsset"`
}

type slugVector struct {
	Input string `json:"input"`
	Slug  string `json:"slug"`
}

func parsedToExpected(parsed model.ParsedNote) dialectExpected {
	out := dialectExpected{
		Title:   parsed.Title,
		Aliases: parsed.Aliases,
	}
	for _, heading := range parsed.Headings {
		out.Headings = append(out.Headings, headingExpected{
			Level: heading.Level,
			Text:  heading.Text,
			Slug:  heading.Slug,
		})
	}
	for _, link := range parsed.Links {
		out.Links = append(out.Links, linkExpected{
			Kind:     string(link.Kind),
			Target:   link.Target,
			Fragment: link.Fragment,
			Display:  link.Display,
			IsEmbed:  link.IsEmbed,
			IsAsset:  link.IsAsset,
		})
	}
	return out
}

func defaultSlugVectors() []slugVector {
	inputs := []string{
		"First Heading",
		"A: Better Title",
		"Multiple   Spaces",
		"Unicode Δ",
		"Cafe\u0301",
		"Heading",
		"  Trimmed  ",
		"---dashes---",
		"Hello, World!",
	}
	vectors := make([]slugVector, 0, len(inputs))
	for _, input := range inputs {
		vectors = append(vectors, slugVector{Input: input, Slug: headingSlug(input)})
	}
	return vectors
}

func TestUpdateDialectFixtures(t *testing.T) {
	if os.Getenv("UPDATE_DIALECT_FIXTURES") != "1" {
		t.Skip("set UPDATE_DIALECT_FIXTURES=1 to regenerate fixtures")
	}
	dir := fixtureDir(t)
	expected := map[string]dialectExpected{}
	for _, name := range []string{"obsidian-syntax.md", "standard-links.md", "unicode-wiki.md", "heading-slugs.md"} {
		source, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			t.Fatal(err)
		}
		parsed := NewParser().Parse(source, strings.TrimSuffix(name, ".md"))
		expected[strings.TrimSuffix(name, ".md")] = parsedToExpected(parsed)
	}
	data, err := json.MarshalIndent(expected, "", "  ")
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "expected.json"), append(data, '\n'), 0o644); err != nil {
		t.Fatal(err)
	}

	slugData, err := json.MarshalIndent(defaultSlugVectors(), "", "  ")
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "heading-slugs.json"), append(slugData, '\n'), 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestDialectFixturesMatchExpected(t *testing.T) {
	expectedRaw, err := os.ReadFile(fixturePath(t, "expected.json"))
	if err != nil {
		t.Fatal(err)
	}
	var expected map[string]dialectExpected
	if err := json.Unmarshal(expectedRaw, &expected); err != nil {
		t.Fatal(err)
	}

	for basename, want := range expected {
		source, err := os.ReadFile(fixturePath(t, basename+".md"))
		if err != nil {
			t.Fatal(err)
		}
		got := parsedToExpected(NewParser().Parse(source, basename))
		if got.Title != want.Title {
			t.Fatalf("%s title = %q, want %q", basename, got.Title, want.Title)
		}
		if len(got.Aliases) != len(want.Aliases) {
			t.Fatalf("%s aliases = %#v, want %#v", basename, got.Aliases, want.Aliases)
		}
		for i := range want.Aliases {
			if got.Aliases[i] != want.Aliases[i] {
				t.Fatalf("%s aliases[%d] = %q, want %q", basename, i, got.Aliases[i], want.Aliases[i])
			}
		}
		if len(got.Headings) != len(want.Headings) {
			t.Fatalf("%s headings = %#v, want %#v", basename, got.Headings, want.Headings)
		}
		for i := range want.Headings {
			if got.Headings[i] != want.Headings[i] {
				t.Fatalf("%s headings[%d] = %#v, want %#v", basename, i, got.Headings[i], want.Headings[i])
			}
		}
		if len(got.Links) != len(want.Links) {
			t.Fatalf("%s links = %#v, want %#v", basename, got.Links, want.Links)
		}
		for i := range want.Links {
			if got.Links[i] != want.Links[i] {
				t.Fatalf("%s links[%d] = %#v, want %#v", basename, i, got.Links[i], want.Links[i])
			}
		}
	}
}

func TestHeadingSlugVectors(t *testing.T) {
	raw, err := os.ReadFile(fixturePath(t, "heading-slugs.json"))
	if err != nil {
		t.Fatal(err)
	}
	var vectors []slugVector
	if err := json.Unmarshal(raw, &vectors); err != nil {
		t.Fatal(err)
	}
	for _, vector := range vectors {
		if got := headingSlug(vector.Input); got != vector.Slug {
			t.Fatalf("headingSlug(%q) = %q, want %q", vector.Input, got, vector.Slug)
		}
	}
}
