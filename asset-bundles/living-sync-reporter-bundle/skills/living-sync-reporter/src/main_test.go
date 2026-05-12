package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestNormalizeStatusAliases(t *testing.T) {
	cases := map[string]string{
		"started":   "IN_PROGRESS",
		"running":   "IN_PROGRESS",
		"completed": "DONE",
		"finished":  "DONE",
		"blocked":   "BLOCKED",
	}

	for input, expected := range cases {
		if got := normalizeStatus(input); got != expected {
			t.Fatalf("normalizeStatus(%q) = %q, want %q", input, got, expected)
		}
	}
}

func TestHookPayloadIgnoresMalformedJson(t *testing.T) {
	payload := hookPayload(strings.NewReader("not json {"))
	if len(payload) != 0 {
		t.Fatalf("hookPayload returned %#v, want empty map", payload)
	}
}

func TestChangedFilesCollectsToolInputPaths(t *testing.T) {
	payload := map[string]any{
		"tool_input": map[string]any{
			"file_path": "backend/src/main.go",
			"edits": []any{
				map[string]any{"file_path": "frontend/src/app.tsx"},
				map[string]any{"file_path": "backend/src/main.go"},
			},
		},
	}

	files := changedFiles(payload)
	expected := []string{"backend/src/main.go", "frontend/src/app.tsx"}
	if len(files) != len(expected) {
		t.Fatalf("changedFiles length = %d, want %d: %#v", len(files), len(expected), files)
	}
	for i := range expected {
		if files[i] != expected[i] {
			t.Fatalf("changedFiles[%d] = %q, want %q", i, files[i], expected[i])
		}
	}
}

func TestCommandExitCodeFindsNestedStringValue(t *testing.T) {
	payload := map[string]any{
		"tool_response": map[string]any{
			"result": map[string]any{"exit_code": "1"},
		},
	}

	if got := commandExitCode(payload); got != 1 {
		t.Fatalf("commandExitCode = %d, want 1", got)
	}
}

func TestCollectDoneImportsSkipsWhenFeatureMetadataMissing(t *testing.T) {
	projectDir := t.TempDir()
	donePath := filepath.Join(projectDir, "docs", "features", "51-feature-done-markdown-import-via-mcp-done.md")
	if err := os.MkdirAll(filepath.Dir(donePath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(donePath, []byte("# Done\ncontent"), 0o644); err != nil {
		t.Fatal(err)
	}

	requests, err := collectDoneImports(projectDir, "", []string{donePath}, reporterState{})
	if err != nil {
		t.Fatalf("collectDoneImports returned error: %v", err)
	}
	if len(requests) != 0 {
		t.Fatalf("collectDoneImports returned %#v, want no imports without feature metadata", requests)
	}
}

func TestCollectDoneImportsSkipsAlreadyImportedDigest(t *testing.T) {
	projectDir := t.TempDir()
	featurePath := filepath.Join(projectDir, "docs", "features", "51-feature-done-markdown-import-via-mcp.md")
	donePath := filepath.Join(projectDir, "docs", "features", "51-feature-done-markdown-import-via-mcp-done.md")
	if err := os.MkdirAll(filepath.Dir(donePath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(featurePath, []byte("---\nfeature_id: feature-51\n---\n# Feature 51\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	content := []byte("# Done\nsame content")
	if err := os.WriteFile(donePath, content, 0o644); err != nil {
		t.Fatal(err)
	}

	digest := doneImportDigest("feature-51", content)
	requests, err := collectDoneImports(projectDir, "", []string{donePath}, reporterState{
		ImportedDoneFiles: map[string]string{filepath.ToSlash(donePath): digest},
	})
	if err != nil {
		t.Fatalf("collectDoneImports returned error: %v", err)
	}
	if len(requests) != 0 {
		t.Fatalf("collectDoneImports returned %#v, want no imports for unchanged digest", requests)
	}
}

func TestCollectDoneImportsReturnsChangedDoneMarkdownOnce(t *testing.T) {
	projectDir := t.TempDir()
	featurePath := filepath.Join(projectDir, "docs", "features", "51-feature-done-markdown-import-via-mcp.md")
	donePath := filepath.Join(projectDir, "docs", "features", "51-feature-done-markdown-import-via-mcp-done.md")
	if err := os.MkdirAll(filepath.Dir(donePath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(featurePath, []byte("---\nfeature_id: feature-51\n---\n# Feature 51\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	content := []byte("# Feature 51\nUpdated summary")
	if err := os.WriteFile(donePath, content, 0o644); err != nil {
		t.Fatal(err)
	}

	requests, err := collectDoneImports(projectDir, "", []string{donePath, donePath}, reporterState{})
	if err != nil {
		t.Fatalf("collectDoneImports returned error: %v", err)
	}
	if len(requests) != 1 {
		t.Fatalf("collectDoneImports length = %d, want 1: %#v", len(requests), requests)
	}
	if requests[0].FeatureID != "feature-51" {
		t.Fatalf("FeatureID = %q, want feature-51", requests[0].FeatureID)
	}
	if requests[0].FileName != "51-feature-done-markdown-import-via-mcp-done.md" {
		t.Fatalf("FileName = %q", requests[0].FileName)
	}
	if requests[0].Markdown != string(content) {
		t.Fatalf("Markdown = %q, want %q", requests[0].Markdown, string(content))
	}
}

func TestProjectIDFromLivingSyncBaseURL(t *testing.T) {
	projectID, err := projectIDFromLivingSyncBaseURL("http://localhost:8080/api/v1/projects/project-123/living-sync/mcp")
	if err != nil {
		t.Fatalf("projectIDFromLivingSyncBaseURL returned error: %v", err)
	}
	if projectID != "project-123" {
		t.Fatalf("projectID = %q, want project-123", projectID)
	}
}

func TestFeatureIDFromFeatureMarkdown(t *testing.T) {
	projectDir := t.TempDir()
	featurePath := filepath.Join(projectDir, "docs", "features", "51-feature-done-markdown-import-via-mcp.md")
	if err := os.MkdirAll(filepath.Dir(featurePath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(featurePath, []byte("---\nfeature_id: 7f3d8d5d-abc\n---\n# Feature 51\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	featureID, err := featureIDFromFeatureMarkdown(featurePath)
	if err != nil {
		t.Fatalf("featureIDFromFeatureMarkdown returned error: %v", err)
	}
	if featureID != "7f3d8d5d-abc" {
		t.Fatalf("featureID = %q, want 7f3d8d5d-abc", featureID)
	}
}
