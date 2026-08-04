package markdown

import (
	"bytes"
	"fmt"
	"net/url"
	"path"
	"regexp"
	"sort"
	"strings"
	"unicode"

	"github.com/okayt/vaultist/server/internal/model"
	"github.com/yuin/goldmark"
	"github.com/yuin/goldmark/ast"
	"github.com/yuin/goldmark/parser"
	gmtext "github.com/yuin/goldmark/text"
	"gopkg.in/yaml.v3"
)

type Parser struct {
	markdown goldmark.Markdown
}

func NewParser() *Parser {
	return &Parser{markdown: goldmark.New(goldmark.WithParserOptions(parser.WithAutoHeadingID()))}
}

func (p *Parser) Parse(source []byte, fallbackTitle string) model.ParsedNote {
	frontmatter, body, bodyOffset := splitFrontmatter(source)
	title, aliases := parseFrontmatter(frontmatter)
	if strings.TrimSpace(title) == "" {
		title = fallbackTitle
	}
	result := model.ParsedNote{Title: title, Aliases: aliases}
	doc := p.markdown.Parser().Parse(gmtext.NewReader(body))
	_ = ast.Walk(doc, func(node ast.Node, entering bool) (ast.WalkStatus, error) {
		if !entering {
			return ast.WalkContinue, nil
		}
		switch n := node.(type) {
		case *ast.Heading:
			text := nodeText(n, body)
			result.Headings = append(result.Headings, model.Heading{
				Level: n.Level, Text: text, Slug: headingSlug(text),
			})
		case *ast.Image:
			target := decodeTarget(string(n.Destination))
			offset := nodeOffset(n)
			line, column, context := position(source, offset+bodyOffset)
			result.Links = append(result.Links, model.LinkOccurrence{
				Kind: model.LinkImage, Raw: string(n.Destination), Target: target,
				Display: nodeText(n, body), Line: line, Column: column, Context: context,
				IsEmbed: true, IsAsset: true,
			})
		case *ast.Link:
			target := decodeTarget(string(n.Destination))
			if isExternal(target) {
				return ast.WalkContinue, nil
			}
			target, fragment := splitFragment(target)
			offset := nodeOffset(n)
			line, column, context := position(source, offset+bodyOffset)
			result.Links = append(result.Links, model.LinkOccurrence{
				Kind: model.LinkMarkdown, Raw: string(n.Destination), Target: target,
				Fragment: fragment, Display: nodeText(n, body), Line: line, Column: column,
				Context: context, IsAsset: !isMarkdownTarget(target),
			})
		}
		return ast.WalkContinue, nil
	})
	parseWikiSource(body, bodyOffset, source, &result)
	sort.SliceStable(result.Links, func(i, j int) bool {
		if result.Links[i].Line != result.Links[j].Line {
			return result.Links[i].Line < result.Links[j].Line
		}
		return result.Links[i].Column < result.Links[j].Column
	})
	for _, link := range result.Links {
		if link.IsAsset {
			result.Attachments = appendUnique(result.Attachments, link.Target)
		}
	}
	return result
}

func splitFrontmatter(source []byte) ([]byte, []byte, int) {
	if !bytes.HasPrefix(source, []byte("---\n")) && !bytes.HasPrefix(source, []byte("---\r\n")) {
		return nil, source, 0
	}
	start := bytes.IndexByte(source, '\n') + 1
	for cursor := start; cursor < len(source); {
		next := bytes.IndexByte(source[cursor:], '\n')
		end := len(source)
		if next >= 0 {
			end = cursor + next
		}
		line := strings.TrimSpace(string(source[cursor:end]))
		if line == "---" || line == "..." {
			bodyStart := end
			if bodyStart < len(source) {
				bodyStart++
			}
			return source[start:cursor], source[bodyStart:], bodyStart
		}
		if next < 0 {
			break
		}
		cursor = end + 1
	}
	return nil, source, 0
}

func parseFrontmatter(source []byte) (string, []string) {
	if len(source) == 0 {
		return "", nil
	}
	var document yaml.Node
	if yaml.Unmarshal(source, &document) != nil || len(document.Content) == 0 {
		return "", nil
	}
	root := document.Content[0]
	if root.Kind != yaml.MappingNode {
		return "", nil
	}
	var title string
	var aliases []string
	for i := 0; i+1 < len(root.Content); i += 2 {
		key, value := root.Content[i], root.Content[i+1]
		switch strings.ToLower(strings.TrimSpace(key.Value)) {
		case "title":
			if value.Kind == yaml.ScalarNode {
				title = strings.TrimSpace(value.Value)
			}
		case "aliases":
			switch value.Kind {
			case yaml.ScalarNode:
				if v := strings.TrimSpace(value.Value); v != "" {
					aliases = append(aliases, v)
				}
			case yaml.SequenceNode:
				for _, child := range value.Content {
					if child.Kind == yaml.ScalarNode && strings.TrimSpace(child.Value) != "" {
						aliases = append(aliases, strings.TrimSpace(child.Value))
					}
				}
			}
		}
	}
	return title, aliases
}

func parseWikiSource(body []byte, bodyOffset int, source []byte, result *model.ParsedNote) {
	for lineStart, fenceCharacter, fenceLength := 0, byte(0), 0; lineStart <= len(body); {
		lineEnd := bytes.IndexByte(body[lineStart:], '\n')
		if lineEnd < 0 {
			lineEnd = len(body)
		} else {
			lineEnd += lineStart
		}
		line := body[lineStart:lineEnd]
		trimmed := bytes.TrimLeft(line, " \t")
		indent := len(line) - len(trimmed)
		if indent <= 3 && len(trimmed) >= 3 && (trimmed[0] == '`' || trimmed[0] == '~') {
			run := byteRun(trimmed, trimmed[0])
			if run >= 3 {
				if fenceCharacter == 0 {
					fenceCharacter, fenceLength = trimmed[0], run
				} else if trimmed[0] == fenceCharacter && run >= fenceLength {
					fenceCharacter, fenceLength = 0, 0
				}
				if lineEnd == len(body) {
					return
				}
				lineStart = lineEnd + 1
				continue
			}
		}
		if fenceCharacter == 0 {
			parseWikiLine(line, lineStart+bodyOffset, source, result)
		}
		if lineEnd == len(body) {
			return
		}
		lineStart = lineEnd + 1
	}
}

func parseWikiLine(line []byte, absoluteOffset int, source []byte, result *model.ParsedNote) {
	inlineCodeRun := 0
	for cursor := 0; cursor < len(line); {
		if line[cursor] == '`' {
			run := byteRun(line[cursor:], '`')
			if inlineCodeRun == 0 {
				inlineCodeRun = run
			} else if run == inlineCodeRun {
				inlineCodeRun = 0
			}
			cursor += run
			continue
		}
		if inlineCodeRun == 0 && cursor+1 < len(line) && line[cursor] == '[' && line[cursor+1] == '[' {
			close := bytes.Index(line[cursor+2:], []byte("]]"))
			if close < 0 {
				return
			}
			close += cursor + 2
			raw := strings.TrimSpace(string(line[cursor+2 : close]))
			if raw != "" {
				embed := cursor > 0 && line[cursor-1] == '!'
				targetPart, display := splitOnce(raw, "|")
				target, fragment := splitFragment(strings.TrimSpace(targetPart))
				target = decodeTarget(target)
				asset := embed && !isMarkdownEmbedTarget(target)
				lineNumber, column, context := position(source, absoluteOffset+cursor)
				kind := model.LinkWiki
				if embed {
					kind = model.LinkWikiEmbed
				}
				result.Links = append(result.Links, model.LinkOccurrence{
					Kind: kind, Raw: raw, Target: target, Fragment: fragment,
					Display: strings.TrimSpace(display), Line: lineNumber, Column: column,
					Context: context, IsEmbed: embed, IsAsset: asset,
				})
			}
			cursor = close + 2
			continue
		}
		cursor++
	}
}

func byteRun(value []byte, match byte) int {
	count := 0
	for count < len(value) && value[count] == match {
		count++
	}
	return count
}

func splitOnce(value, separator string) (string, string) {
	if at := strings.Index(value, separator); at >= 0 {
		return value[:at], value[at+len(separator):]
	}
	return value, ""
}

func splitFragment(value string) (string, string) {
	target, fragment := splitOnce(value, "#")
	return strings.TrimSpace(target), strings.TrimSpace(fragment)
}

func decodeTarget(value string) string {
	decoded, err := url.PathUnescape(strings.TrimSpace(value))
	if err != nil {
		return strings.TrimSpace(value)
	}
	return decoded
}

func isExternal(target string) bool {
	u, err := url.Parse(target)
	return err == nil && (u.IsAbs() || strings.HasPrefix(target, "//") || strings.HasPrefix(target, "mailto:"))
}

func isMarkdownTarget(target string) bool {
	ext := strings.ToLower(path.Ext(target))
	return ext == "" || ext == ".md"
}

var imageExtensions = map[string]bool{
	".png": true, ".jpg": true, ".jpeg": true, ".webp": true,
	".gif": true, ".svg": true,
}

func isMarkdownEmbedTarget(target string) bool {
	ext := strings.ToLower(path.Ext(target))
	return ext == "" || ext == ".md"
}

func nodeText(node ast.Node, source []byte) string {
	var builder strings.Builder
	_ = ast.Walk(node, func(child ast.Node, entering bool) (ast.WalkStatus, error) {
		if entering {
			switch text := child.(type) {
			case *ast.Text:
				builder.Write(text.Segment.Value(source))
			case *ast.String:
				builder.Write(text.Value)
			}
		}
		return ast.WalkContinue, nil
	})
	return strings.TrimSpace(builder.String())
}

func nodeOffset(node ast.Node) int {
	if text, ok := node.(*ast.Text); ok {
		return text.Segment.Start
	}
	for child := node.FirstChild(); child != nil; child = child.NextSibling() {
		if offset := nodeOffset(child); offset >= 0 {
			return offset
		}
	}
	if node.Type() == ast.TypeBlock {
		if lines := node.Lines(); lines != nil && lines.Len() > 0 {
			return lines.At(0).Start
		}
	}
	return -1
}

func position(source []byte, offset int) (int, int, string) {
	if offset < 0 {
		offset = 0
	}
	if offset > len(source) {
		offset = len(source)
	}
	lineStart := bytes.LastIndexByte(source[:offset], '\n') + 1
	lineEndRelative := bytes.IndexByte(source[offset:], '\n')
	lineEnd := len(source)
	if lineEndRelative >= 0 {
		lineEnd = offset + lineEndRelative
	}
	line := bytes.Count(source[:offset], []byte("\n")) + 1
	column := len([]rune(string(source[lineStart:offset]))) + 1
	context := strings.TrimSpace(string(source[lineStart:lineEnd]))
	if len([]rune(context)) > 240 {
		context = string([]rune(context)[:240]) + "…"
	}
	return line, column, context
}

var nonSlug = regexp.MustCompile(`[^\pL\pN\pM -]+`)

func headingSlug(value string) string {
	value = strings.ToLower(strings.TrimSpace(value))
	value = nonSlug.ReplaceAllString(value, "")
	value = strings.Map(func(r rune) rune {
		if unicode.IsSpace(r) {
			return '-'
		}
		return r
	}, value)
	return strings.Trim(value, "-")
}

func appendUnique(values []string, value string) []string {
	for _, existing := range values {
		if existing == value {
			return values
		}
	}
	return append(values, value)
}

func ValidateParsedNote(note model.ParsedNote) error {
	if strings.TrimSpace(note.Title) == "" {
		return fmt.Errorf("title is empty")
	}
	return nil
}
