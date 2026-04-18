const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export async function apiFetch<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...options?.headers,
    },
    ...options,
  });

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error(error.message || `API error: ${res.status}`);
  }

  return res.json();
}

// ─── Domain Types ────────────────────────────────────────────────────────────

export type StepType = "IDEA" | "PROBLEM" | "TARGET_AUDIENCE" | "SCOPE" | "MVP" | "FEATURES" | "ARCHITECTURE" | "BACKEND" | "FRONTEND";
export type StepStatus = "OPEN" | "IN_PROGRESS" | "COMPLETED";

// ─── Feature Graph Types ──────────────────────────────────────────────────────

export type FeatureScope = "FRONTEND" | "BACKEND";

export interface GraphPosition { x: number; y: number }

export interface WizardFeature {
  id: string;
  title: string;
  scopes: FeatureScope[];
  description: string;
  scopeFields: Record<string, string>;
  position: GraphPosition;
}

export interface WizardFeatureEdge {
  id: string;
  from: string;
  to: string;
}

export interface WizardFeatureGraph {
  features: WizardFeature[];
  edges: WizardFeatureEdge[];
}
export type ProjectStatus = "DRAFT" | "IN_PROGRESS" | "COMPLETED";

export interface FlowStep {
  stepType: StepType;
  status: StepStatus;
  updatedAt: string;
}

export interface FlowState {
  projectId: string;
  steps: FlowStep[];
  currentStep: StepType;
}

export interface Project {
  id: string;
  name: string;
  ownerId: string;
  status: ProjectStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectResponse {
  project: Project;
  flowState: FlowState;
}

export interface CreateProjectRequest {
  name: string;
  idea: string;
}

export interface ChatRequest {
  message: string;
  locale: string;
}

export interface ChatResponse {
  message: string;
  flowStateChanged: boolean;
  currentStep: string;
  decisionId: string | null;
  clarificationId: string | null;
}

// ─── Clarification Types ─────────────────────────────────────────────────────

export type ClarificationStatus = "OPEN" | "ANSWERED";

export interface Clarification {
  id: string;
  projectId: string;
  stepType: StepType;
  question: string;
  reason: string;
  status: ClarificationStatus;
  answer: string | null;
  createdAt: string;
  answeredAt: string | null;
}

export interface AnswerClarificationRequest {
  answer: string;
}

// ─── Decision Types ──────────────────────────────────────────────────────────

export type DecisionStatus = "PENDING" | "RESOLVED";

export interface DecisionOption {
  id: string;
  label: string;
  pros: string[];
  cons: string[];
  recommended: boolean;
}

export interface Decision {
  id: string;
  projectId: string;
  stepType: StepType;
  title: string;
  options: DecisionOption[];
  recommendation: string;
  status: DecisionStatus;
  chosenOptionId: string | null;
  rationale: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface CreateDecisionRequest {
  title: string;
  stepType: StepType;
}

export interface ResolveDecisionRequest {
  chosenOptionId: string;
  rationale: string;
}

// ─── Check Types ─────────────────────────────────────────────────────────────

export type CheckSeverity = "ERROR" | "WARNING" | "INFO";

export interface CheckResult {
  id: string;
  severity: CheckSeverity;
  category: string;
  message: string;
  relatedArtifact: string | null;
  suggestedFix: string | null;
}

export interface CheckSummary {
  errors: number;
  warnings: number;
  infos: number;
  passed: boolean;
}

export interface CheckReport {
  projectId: string;
  results: CheckResult[];
  checkedAt: string;
  summary: CheckSummary;
}

// ─── Task Types ──────────────────────────────────────────────────────────────

export type TaskType = "EPIC" | "STORY" | "TASK";
export type TaskItemStatus = "TODO" | "IN_PROGRESS" | "DONE";

export interface SpecTask {
  id: string;
  projectId: string;
  parentId: string | null;
  type: TaskType;
  title: string;
  description: string;
  estimate: string;
  priority: number;
  status: TaskItemStatus;
  specSection: StepType | null;
  dependencies: string[];
  createdAt: string;
  updatedAt: string;
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  estimate?: string;
  priority?: number;
  status?: TaskItemStatus;
  parentId?: string;
  dependencies?: string[];
}

export interface CoverageMap {
  [key: string]: boolean;
}

// ─── File Explorer Types ─────────────────────────────────────────────────────

export interface FileEntry {
  name: string;
  path: string;
  isDirectory: boolean;
  size: number;
  children?: FileEntry[];
}

export interface FileContent {
  path: string;
  name: string;
  content: string;
  language: string;
  lineCount: number;
}

// ─── Handoff Types ──────────────────────────────────────────────────────────

export interface HandoffPreview {
  claudeMd: string;
  agentsMd: string;
  implementationOrder: string;
  format: string;
}

export interface HandoffExportRequest {
  format?: string;
  claudeMd?: string;
  agentsMd?: string;
  implementationOrder?: string;
}

// ─── API Endpoints ──────────────────────────────────────────────────────────

export async function getHealth(): Promise<{ status: string; timestamp: string }> {
  return apiFetch("/api/health");
}

export async function listProjects(): Promise<Project[]> {
  return apiFetch<Project[]>("/api/v1/projects");
}

export async function createProject(data: CreateProjectRequest): Promise<ProjectResponse> {
  return apiFetch<ProjectResponse>("/api/v1/projects", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function getProject(id: string): Promise<ProjectResponse> {
  return apiFetch<ProjectResponse>(`/api/v1/projects/${id}`);
}

export async function deleteProject(id: string): Promise<void> {
  await fetch(`${API_BASE}/api/v1/projects/${id}`, { method: "DELETE" });
}

export async function getFlowState(projectId: string): Promise<FlowState> {
  return apiFetch<FlowState>(`/api/v1/projects/${projectId}/flow`);
}

export async function sendChatMessage(projectId: string, data: ChatRequest): Promise<ChatResponse> {
  return apiFetch<ChatResponse>(`/api/v1/projects/${projectId}/agent/chat`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function listDecisions(projectId: string): Promise<Decision[]> {
  return apiFetch<Decision[]>(`/api/v1/projects/${projectId}/decisions`);
}

export async function createDecision(projectId: string, data: CreateDecisionRequest): Promise<Decision> {
  return apiFetch<Decision>(`/api/v1/projects/${projectId}/decisions`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function getDecision(projectId: string, decisionId: string): Promise<Decision> {
  return apiFetch<Decision>(`/api/v1/projects/${projectId}/decisions/${decisionId}`);
}

export async function resolveDecision(projectId: string, decisionId: string, data: ResolveDecisionRequest): Promise<Decision> {
  return apiFetch<Decision>(`/api/v1/projects/${projectId}/decisions/${decisionId}/resolve`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function listClarifications(projectId: string): Promise<Clarification[]> {
  return apiFetch<Clarification[]>(`/api/v1/projects/${projectId}/clarifications`);
}

export async function getClarification(projectId: string, clarificationId: string): Promise<Clarification> {
  return apiFetch<Clarification>(`/api/v1/projects/${projectId}/clarifications/${clarificationId}`);
}

export async function proposeFeatures(projectId: string): Promise<WizardFeatureGraph> {
  return apiFetch<WizardFeatureGraph>(`/api/v1/projects/${projectId}/features/propose`, {
    method: "POST",
  });
}

export async function answerClarification(projectId: string, clarificationId: string, data: AnswerClarificationRequest): Promise<Clarification> {
  return apiFetch<Clarification>(`/api/v1/projects/${projectId}/clarifications/${clarificationId}/answer`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function listTasks(projectId: string): Promise<SpecTask[]> {
  return apiFetch<SpecTask[]>(`/api/v1/projects/${projectId}/tasks`);
}

export async function generatePlan(projectId: string): Promise<SpecTask[]> {
  return apiFetch<SpecTask[]>(`/api/v1/projects/${projectId}/tasks/generate`, { method: "POST" });
}

export async function getTask(projectId: string, taskId: string): Promise<SpecTask> {
  return apiFetch<SpecTask>(`/api/v1/projects/${projectId}/tasks/${taskId}`);
}

export async function updateTask(projectId: string, taskId: string, data: UpdateTaskRequest): Promise<SpecTask> {
  return apiFetch<SpecTask>(`/api/v1/projects/${projectId}/tasks/${taskId}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteTask(projectId: string, taskId: string): Promise<void> {
  await fetch(`${API_BASE}/api/v1/projects/${projectId}/tasks/${taskId}`, { method: "DELETE" });
}

export async function getTaskCoverage(projectId: string): Promise<CoverageMap> {
  return apiFetch<CoverageMap>(`/api/v1/projects/${projectId}/tasks/coverage`);
}

export async function runChecks(projectId: string): Promise<CheckReport> {
  return apiFetch<CheckReport>(`/api/v1/projects/${projectId}/checks`, { method: "POST" });
}

export async function getCheckResults(projectId: string): Promise<CheckReport> {
  return apiFetch<CheckReport>(`/api/v1/projects/${projectId}/checks/results`);
}

export async function getHandoffPreview(projectId: string, format: string = "claude-code"): Promise<HandoffPreview> {
  return apiFetch<HandoffPreview>(`/api/v1/projects/${projectId}/handoff/preview?format=${format}`, { method: "POST" });
}

export async function exportHandoff(projectId: string, request: HandoffExportRequest = {}): Promise<Blob> {
  const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/handoff/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!res.ok) throw new Error("Handoff export failed");
  return res.blob();
}

// ─── Wizard Types ─────────────────────────────────────────────────────────────

export interface WizardStepData {
  fields: Record<string, any>;
  completedAt: string | null;
}

export interface WizardData {
  projectId: string;
  steps: Record<string, WizardStepData>;
}

// ─── Wizard API ───────────────────────────────────────────────────────────────

export async function getWizardData(projectId: string): Promise<WizardData> {
  return apiFetch<WizardData>(`/api/v1/projects/${projectId}/wizard`);
}

export async function saveWizardData(projectId: string, data: WizardData): Promise<WizardData> {
  return apiFetch<WizardData>(`/api/v1/projects/${projectId}/wizard`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function saveWizardStep(projectId: string, step: string, data: WizardStepData): Promise<WizardData> {
  return apiFetch<WizardData>(`/api/v1/projects/${projectId}/wizard/${step}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function listProjectFiles(projectId: string): Promise<FileEntry[]> {
  return apiFetch<FileEntry[]>(`/api/v1/projects/${projectId}/files`);
}

export async function readProjectFile(projectId: string, filePath: string): Promise<FileContent> {
  return apiFetch<FileContent>(`/api/v1/projects/${projectId}/files/${filePath}`);
}

export async function exportProject(
  projectId: string,
  options: { includeDecisions?: boolean; includeClarifications?: boolean; includeTasks?: boolean } = {}
): Promise<Blob> {
  const res = await fetch(`${API_BASE}/api/v1/projects/${projectId}/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      includeDecisions: options.includeDecisions ?? true,
      includeClarifications: options.includeClarifications ?? true,
      includeTasks: options.includeTasks ?? true,
    }),
  });
  if (!res.ok) throw new Error("Export failed");
  return res.blob();
}

// ─── Wizard Chat Types ───────────────────────────────────────────────────────

export interface WizardStepCompleteRequest {
  step: string;
  fields: Record<string, any>;
  locale: string;
}

export interface WizardStepCompleteResponse {
  message: string;
  nextStep: string | null;
  exportTriggered: boolean;
  decisionId?: string | null;
  clarificationId?: string | null;
}

export async function completeWizardStep(
  projectId: string,
  data: WizardStepCompleteRequest
): Promise<WizardStepCompleteResponse> {
  return apiFetch<WizardStepCompleteResponse>(
    `/api/v1/projects/${projectId}/agent/wizard-step-complete`,
    { method: "POST", body: JSON.stringify(data) }
  );
}
