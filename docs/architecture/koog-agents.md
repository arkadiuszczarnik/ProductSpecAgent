# Architecture: Koog Agent Integration

## Überblick
JetBrains Koog wird als AI-Agent-Framework innerhalb des Spring Boot Backends eingesetzt. Jeder Kern-Feature-Bereich hat einen spezialisierten Agent.

## Agents

| Agent | Feature | Aufgabe |
|-------|---------|--------|
| `IdeaToSpecAgent` | Feature 1 | Analysiert Freitext-Ideen, führt durch den Spec-Prozess |
| `DecisionAgent` | Feature 2 | Generiert Entscheidungsoptionen mit Pro/Contra |
| `ClarificationAgent` | Feature 3 | Erkennt Lücken/Widersprüche, stellt Rückfragen |
| `PlanGeneratorAgent` | Feature 4 | Erzeugt Task-Hierarchie aus fertiger Spec |
| `ConsistencyCheckAgent` | Feature 7 | Prüft Artefakte auf Konsistenz |

## Integration
- Agents laufen innerhalb des Spring Boot Prozesses (kein separater Service)
- Konfiguration (LLM-Provider, Modell, Temperature) via `application.yml`
- Jeder Agent bekommt den aktuellen Projekt-Kontext als Input injiziert
- Agent-Responses werden strukturiert (JSON) zurückgegeben

## Kontext-Injection
Jeder Agent erhält:
- Projekt-Metadaten (`project.json`)
- Bisherige Wizard-Schritte aus `wizard.json`
- Bisherige Entscheidungen (`decisions/`)
- Offene Clarifications (`clarifications/`)
- Bei Export/Handoff: die finale Spec aus `spec/spec.md`

## Konfiguration (application.yml)
```yaml
koog:
  provider: anthropic  # oder openai, local
  model: claude-sonnet-4-6
  temperature: 0.7
  agents:
    idea-to-spec:
      system-prompt: "Du bist ein erfahrener Product Owner..."
    decision:
      system-prompt: "Du hilfst bei Produktentscheidungen..."
    clarification:
      system-prompt: "Du analysierst Produktspezifikationen auf Lücken..."
    plan-generator:
      system-prompt: "Du erzeugst Implementierungspläne..."
    consistency-check:
      system-prompt: "Du prüfst Produktartefakte auf Konsistenz..."
```
