package main

import (
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
