package main

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

var endpoints = map[string]string{
	"feature-progress": "/report-feature-progress",
	"test-run":         "/report-test-run",
	"token-usage":      "/report-token-usage",
	"code-changes":     "/report-code-changes",
	"sync-note":        "/report-sync-note",
}

var testCommandMarkers = []string{
	"gradlew test",
	"gradle test",
	"npm test",
	"npm run test",
	"npm run test:e2e",
	"npm run lint",
	"npm run build",
	"pnpm test",
	"pnpm run test",
	"pnpm run lint",
	"pnpm run build",
}

type stringList []string

func (s *stringList) String() string {
	return strings.Join(*s, ",")
}

func (s *stringList) Set(value string) error {
	*s = append(*s, value)
	return nil
}

func main() {
	if len(os.Args) < 2 {
		usageAndExit()
	}

	var err error
	switch os.Args[1] {
	case "session-start":
		err = safePost("sync-note", map[string]any{"severity": "INFO", "message": "Agent session started."})
	case "stop":
		err = safePost("sync-note", map[string]any{"severity": "INFO", "message": "Agent turn completed."})
	case "post-tool-use":
		err = handlePostToolUse(os.Stdin)
	case "code-change":
		err = handleCodeChange(os.Stdin)
	case "feature-progress":
		err = handleFeatureProgress(os.Args[2:])
	case "test-run":
		err = handleTestRun(os.Args[2:])
	case "code-changes":
		err = handleCodeChanges(os.Args[2:])
	case "token-usage":
		err = handleTokenUsage(os.Args[2:])
	case "sync-note":
		err = handleSyncNote(os.Args[2:])
	case "-h", "--help", "help":
		usageAndExit()
	default:
		err = fmt.Errorf("unknown command: %s", os.Args[1])
	}

	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func usageAndExit() {
	fmt.Fprintln(os.Stderr, "usage: living-sync-reporter <session-start|stop|post-tool-use|code-change|feature-progress|test-run|code-changes|token-usage|sync-note> [flags]")
	os.Exit(2)
}

func baseURL() (string, error) {
	if value := strings.TrimSpace(os.Getenv("LIVING_SYNC_BASE_URL")); value != "" {
		return strings.TrimRight(value, "/"), nil
	}

	projectDir := os.Getenv("CLAUDE_PROJECT_DIR")
	if projectDir == "" {
		var err error
		projectDir, err = os.Getwd()
		if err != nil {
			return "", err
		}
	}

	configPath := os.Getenv("LIVING_SYNC_CONFIG")
	if configPath == "" {
		configPath = filepath.Join(projectDir, ".claude", "living-sync.json")
	}

	content, err := os.ReadFile(configPath)
	if err != nil {
		return "", err
	}
	var config struct {
		LivingSyncBaseURL string `json:"livingSyncBaseUrl"`
	}
	if err := json.Unmarshal(content, &config); err != nil {
		return "", err
	}
	if strings.TrimSpace(config.LivingSyncBaseURL) == "" {
		return "", errors.New("livingSyncBaseUrl missing in config")
	}
	return strings.TrimRight(config.LivingSyncBaseURL, "/"), nil
}

func post(kind string, payload map[string]any) error {
	suffix, ok := endpoints[kind]
	if !ok {
		return fmt.Errorf("unknown endpoint kind: %s", kind)
	}
	base, err := baseURL()
	if err != nil {
		return err
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	timeout := 2 * time.Second
	if raw := strings.TrimSpace(os.Getenv("LIVING_SYNC_TIMEOUT_SECONDS")); raw != "" {
		if seconds, err := strconv.ParseFloat(raw, 64); err == nil && seconds > 0 {
			timeout = time.Duration(seconds * float64(time.Second))
		}
	}

	return postHTTP(base+suffix, body, timeout)
}

func postHTTP(rawURL string, body []byte, timeout time.Duration) error {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return err
	}
	if parsed.Scheme != "http" {
		return fmt.Errorf("unsupported scheme: %s", parsed.Scheme)
	}
	host := parsed.Host
	if !strings.Contains(host, ":") {
		host += ":80"
	}
	conn, err := net.DialTimeout("tcp", host, timeout)
	if err != nil {
		return err
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(timeout))

	path := parsed.RequestURI()
	if path == "" {
		path = "/"
	}
	request := bytes.Buffer{}
	fmt.Fprintf(&request, "POST %s HTTP/1.1\r\n", path)
	fmt.Fprintf(&request, "Host: %s\r\n", parsed.Host)
	fmt.Fprint(&request, "Content-Type: application/json\r\n")
	fmt.Fprintf(&request, "Content-Length: %d\r\n", len(body))
	fmt.Fprint(&request, "Connection: close\r\n\r\n")
	request.Write(body)
	if _, err := conn.Write(request.Bytes()); err != nil {
		return err
	}

	reader := bufio.NewReader(conn)
	statusLine, err := reader.ReadString('\n')
	if err != nil {
		return err
	}
	parts := strings.Fields(statusLine)
	if len(parts) < 2 {
		return fmt.Errorf("invalid HTTP response: %s", strings.TrimSpace(statusLine))
	}
	statusCode, err := strconv.Atoi(parts[1])
	if err != nil {
		return err
	}
	_, _ = io.Copy(io.Discard, reader)
	if statusCode < 200 || statusCode >= 300 {
		return fmt.Errorf("HTTP %d", statusCode)
	}
	return nil
}

func safePost(kind string, payload map[string]any) error {
	if err := post(kind, payload); err != nil {
		fmt.Fprintf(os.Stderr, "living-sync reporter skipped %s: %v\n", kind, err)
	}
	return nil
}

func normalizeStatus(value string) string {
	normalized := strings.ToUpper(strings.ReplaceAll(strings.TrimSpace(value), "-", "_"))
	switch normalized {
	case "STARTED", "START", "RUNNING":
		return "IN_PROGRESS"
	case "COMPLETED", "COMPLETE", "FINISHED":
		return "DONE"
	default:
		return normalized
	}
}

func hookPayload(reader io.Reader) map[string]any {
	raw, err := io.ReadAll(reader)
	if err != nil || strings.TrimSpace(string(raw)) == "" {
		return map[string]any{}
	}
	var payload map[string]any
	if err := json.Unmarshal(raw, &payload); err != nil {
		return map[string]any{}
	}
	return payload
}

func nested(data map[string]any, keys ...string) any {
	var current any = data
	for _, key := range keys {
		asMap, ok := current.(map[string]any)
		if !ok {
			return nil
		}
		current = asMap[key]
	}
	return current
}

func commandExitCode(data map[string]any) int {
	paths := [][]string{
		{"tool_response", "exit_code"},
		{"tool_response", "status"},
		{"tool_response", "code"},
		{"tool_response", "result", "exit_code"},
	}
	for _, path := range paths {
		switch value := nested(data, path...).(type) {
		case float64:
			return int(value)
		case int:
			return value
		case string:
			if parsed, err := strconv.Atoi(value); err == nil {
				return parsed
			}
		}
	}
	return 0
}

func changedFiles(data map[string]any) []string {
	toolInput, ok := data["tool_input"].(map[string]any)
	if !ok {
		return nil
	}
	seen := map[string]bool{}
	for _, key := range []string{"file_path", "path"} {
		if value, ok := toolInput[key].(string); ok && value != "" {
			seen[value] = true
		}
	}
	if edits, ok := toolInput["edits"].([]any); ok {
		for _, edit := range edits {
			if asMap, ok := edit.(map[string]any); ok {
				if value, ok := asMap["file_path"].(string); ok && value != "" {
					seen[value] = true
				}
			}
		}
	}
	files := make([]string, 0, len(seen))
	for file := range seen {
		files = append(files, file)
	}
	sort.Strings(files)
	return files
}

func handlePostToolUse(reader io.Reader) error {
	data := hookPayload(reader)
	command, ok := nested(data, "tool_input", "command").(string)
	if !ok {
		return nil
	}
	compactCommand := strings.Join(strings.Fields(command), " ")
	matches := false
	for _, marker := range testCommandMarkers {
		if strings.Contains(compactCommand, marker) {
			matches = true
			break
		}
	}
	if !matches {
		return nil
	}
	exitCode := commandExitCode(data)
	status := "passed"
	if exitCode != 0 {
		status = "failed"
	}
	return safePost("test-run", map[string]any{
		"command":   truncate(compactCommand, 240),
		"status":    status,
		"summary":   fmt.Sprintf("Command %s: %s", status, truncate(compactCommand, 160)),
		"passed":    boolInt(exitCode == 0),
		"failed":    boolInt(exitCode != 0),
		"featureId": os.Getenv("LIVING_SYNC_FEATURE_ID"),
		"agentName": "claude-code",
	})
}

func handleCodeChange(reader io.Reader) error {
	files := changedFiles(hookPayload(reader))
	if len(files) == 0 {
		return nil
	}
	return safePost("code-changes", map[string]any{
		"summary":   "Files changed by coding agent.",
		"files":     files,
		"featureId": os.Getenv("LIVING_SYNC_FEATURE_ID"),
		"agentName": "claude-code",
	})
}

func handleFeatureProgress(args []string) error {
	fs := flag.NewFlagSet("feature-progress", flag.ContinueOnError)
	task, agent := commonFlags(fs)
	feature := fs.String("feature", "", "")
	status := fs.String("status", "IN_PROGRESS", "")
	summary := fs.String("summary", "", "")
	var evidence stringList
	fs.Var(&evidence, "evidence", "")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *feature == "" || *summary == "" {
		return errors.New("feature-progress requires --feature and --summary")
	}
	return safePost("feature-progress", map[string]any{
		"featureId": *feature,
		"status":    normalizeStatus(*status),
		"summary":   *summary,
		"evidence":  []string(evidence),
		"taskId":    *task,
		"agentName": *agent,
	})
}

func handleTestRun(args []string) error {
	fs := flag.NewFlagSet("test-run", flag.ContinueOnError)
	task, agent := commonFlags(fs)
	feature := fs.String("feature", "", "")
	command := fs.String("command", "", "")
	status := fs.String("status", "", "")
	summary := fs.String("summary", "", "")
	passed := fs.Int("passed", 0, "")
	failed := fs.Int("failed", 0, "")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *command == "" || *status == "" || *summary == "" {
		return errors.New("test-run requires --command, --status, and --summary")
	}
	return safePost("test-run", map[string]any{
		"command":   *command,
		"status":    *status,
		"summary":   *summary,
		"passed":    *passed,
		"failed":    *failed,
		"featureId": *feature,
		"taskId":    *task,
		"agentName": *agent,
	})
}

func handleCodeChanges(args []string) error {
	fs := flag.NewFlagSet("code-changes", flag.ContinueOnError)
	task, agent := commonFlags(fs)
	feature := fs.String("feature", "", "")
	summary := fs.String("summary", "", "")
	var files stringList
	var commits stringList
	fs.Var(&files, "file", "")
	fs.Var(&commits, "commit", "")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *summary == "" {
		return errors.New("code-changes requires --summary")
	}
	return safePost("code-changes", map[string]any{
		"summary":   *summary,
		"files":     []string(files),
		"commits":   []string(commits),
		"featureId": *feature,
		"taskId":    *task,
		"agentName": *agent,
	})
}

func handleTokenUsage(args []string) error {
	fs := flag.NewFlagSet("token-usage", flag.ContinueOnError)
	task, agent := commonFlags(fs)
	feature := fs.String("feature", "", "")
	model := fs.String("model", "", "")
	inputTokens := fs.Int("input-tokens", 0, "")
	outputTokens := fs.Int("output-tokens", 0, "")
	totalTokens := fs.Int("total-tokens", -1, "")
	summary := fs.String("summary", "Token usage reported.", "")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *model == "" {
		return errors.New("token-usage requires --model")
	}
	total := *totalTokens
	if total < 0 {
		total = *inputTokens + *outputTokens
	}
	return safePost("token-usage", map[string]any{
		"agentName":    *agent,
		"model":        *model,
		"inputTokens":  *inputTokens,
		"outputTokens": *outputTokens,
		"totalTokens":  total,
		"summary":      *summary,
		"featureId":    *feature,
		"taskId":       *task,
	})
}

func handleSyncNote(args []string) error {
	fs := flag.NewFlagSet("sync-note", flag.ContinueOnError)
	task, agent := commonFlags(fs)
	feature := fs.String("feature", "", "")
	severity := fs.String("severity", "INFO", "")
	message := fs.String("message", "", "")
	suggestedAction := fs.String("suggested-action", "", "")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *message == "" {
		return errors.New("sync-note requires --message")
	}
	return safePost("sync-note", map[string]any{
		"severity":        *severity,
		"message":         *message,
		"suggestedAction": *suggestedAction,
		"featureId":       *feature,
		"taskId":          *task,
		"agentName":       *agent,
	})
}

func commonFlags(fs *flag.FlagSet) (*string, *string) {
	task := fs.String("task", "", "")
	agent := fs.String("agent", "claude-code", "")
	return task, agent
}

func boolInt(value bool) int {
	if value {
		return 1
	}
	return 0
}

func truncate(value string, max int) string {
	if len(value) <= max {
		return value
	}
	return value[:max]
}
