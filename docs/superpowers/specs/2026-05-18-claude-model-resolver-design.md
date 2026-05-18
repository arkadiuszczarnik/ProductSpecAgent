# Claude Model Resolver Design

## Goal
Add first-class Claude model resolution next to the existing OpenAI path so backend agents can run entirely on Anthropic models when configured to do so.

## Scope
- Add a `ClaudeModelResolver` equivalent to `OpenAiModelResolver`.
- Add a single global resolver selection in `agent.models`.
- Keep tier model ids homogeneous for the selected resolver. No mixed OpenAI/Claude tier mapping.
- Keep OpenAI as the repository default.
- Add commented Claude examples to `application.yml` and `application-dev.yml`.

## Architecture
`AgentModelRegistry` currently validates tier ids and resolves them to Koog `LLModel` instances through `resolveOpenAiModel(...)`. The change introduces a resolver selector in `AgentModelsProperties`, backed by a small enum, so the registry can resolve every tier through one consistent provider-specific function.

The new `ClaudeModelResolver` will map supported Anthropic ids from Koog 0.8.0 `AnthropicModels`. The registry remains the single place that converts tier configuration into runtime `LLModel` values. No agent code outside the registry needs provider-specific branching.

## Configuration
Add `agent.models.resolver` with two allowed values:
- `openai`
- `claude`

Runtime configuration remains global. If `resolver: claude`, then `SMALL`, `MEDIUM`, and `LARGE` must all be Claude ids. Invalid ids fail fast during registry initialization, matching the existing OpenAI behavior.

`application.yml` and `application-dev.yml` should:
- keep OpenAI active by default
- show `ai.koog.openai.enabled: true`
- show `ai.koog.anthropic.enabled: false`
- include commented Claude example tier mappings and API key line for quick switching

## Testing
- Add resolver unit tests for supported Claude ids and unknown ids.
- Extend registry tests to verify resolver selection and fail-fast behavior for mismatched ids.
- Run focused backend tests for both resolvers and the registry.

## Non-Goals
- No per-tier provider mixing.
- No UI changes.
- No changes to agent prompts or runtime agent selection logic.
