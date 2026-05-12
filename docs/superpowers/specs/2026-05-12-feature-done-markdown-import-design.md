# Feature Done Markdown Import Design

## Summary

Product-Spec-Agent soll eine weitere MCP-Schnittstelle erhalten, ueber die ein Coding Agent eine `*-done.md` Datei fuer genau ein Projekt-Feature hochladen kann. Product-Spec-Agent analysiert den Markdown-Inhalt nicht per Regex oder AST-Parser, sondern ueber einen dedizierten PSA-Agenten, der daraus eine stabile JSON-Normalform erzeugt.

Das Ergebnis wird gespeichert und als Umsetzungs-Snapshot fuer das betroffene Projekt-Feature genutzt. Dadurch spiegeln Workspace, Living Sync und Feature-Graph nicht nur manuell gemeldete Einzel-Events, sondern auch den zusammengefassten Abschlussstand eines Features.

## Direction

Die gewaehlte Struktur ist eine agentenzentrierte Import-Pipeline:

- MCP-Tool nimmt `projectId`, `featureId`, `fileName` und `markdown` entgegen.
- Das Backend validiert nur technische Voraussetzungen.
- Ein PSA-Agent analysiert den Markdown-Text und antwortet ausschliesslich mit JSON.
- Das Backend speichert Roh-Markdown, Agent-Antwort und einen abgeleiteten Feature-Snapshot.
- UI und Projekt-Feature-Ansichten lesen diesen Snapshot als Umsetzungsmodell.

Die Zuordnung zu einem Projekt-Feature erfolgt ausschliesslich ueber die explizite `featureId`. Die Markdown-Ueberschrift dient nur als Plausibilitaetscheck.

## In Scope

- Neues MCP-Tool fuer Upload eines Feature-Done-Markdowns.
- Neuer PSA-Agent fuer Markdown-zu-JSON-Analyse.
- Persistenz des Roh-Markdowns und des normalisierten Analyse-Ergebnisses.
- Separater Feature-Completion-Snapshot pro Projekt-Feature.
- Rueckspiegelung von `derivedStatus`, Zusammenfassung, offenen Punkten, Abweichungen und Test-Evidenz in Workspace und Feature-Graph.
- Warnungen bei unplausibler Markdown-Ueberschrift.

## Out of Scope

- Regelbasiertes Markdown-Parsing im Anwendungscode.
- Direkte Mutation des `WizardFeature`-Planungsmodells mit Import-Details.
- Mehrere Projekt-Features pro Import.
- Manuelle UI-Uploads ausserhalb der MCP-Schnittstelle.
- Automatische heuristische Feature-Erkennung ohne explizite `featureId`.

## Data Contract

Der Agent soll eine enge JSON-Normalform liefern, damit die Speicherung und spaetere Darstellung stabil bleibt.

Beispiel:

```json
{
  "featureId": "uuid",
  "headerCheck": {
    "matchesExpectedFeature": true,
    "reportedFeatureLabel": "Feature 45: Living-Sync via MCP",
    "warnings": []
  },
  "derivedStatus": "DONE",
  "summary": "Kurze fachliche Zusammenfassung des umgesetzten Stands.",
  "implementedItems": [
    "Neue Domain-Modelle fuer Living-Sync-Events."
  ],
  "deviations": [
    "Spring-WebMVC-JSON-RPC statt Spring-AI-MCP-Starter."
  ],
  "tests": [
    {
      "name": "LivingSyncServiceTest",
      "status": "PRESENT"
    }
  ],
  "openPoints": [
    "Auth-Hardening fuer externe Coding Agents festlegen."
  ],
  "technicalDebt": [
    "Spring AI MCP Transport spaeter produktiv absichern."
  ],
  "warnings": []
}
```

## Persistence

- Roh-Markdown als Artefakt pro Importlauf.
- Living-Sync-Import-Event fuer Audit und Verlauf.
- Separater Snapshot pro Feature, damit Planungsdaten und Umsetzungsdaten getrennt bleiben.

Empfohlene Trennung:

- `WizardFeatureGraph` bleibt das Planungsmodell.
- `FeatureCompletionSnapshot` wird das Umsetzungsmodell.

## Validation

- Projekt und `featureId` muessen existieren.
- `markdown` darf nicht leer sein.
- Agent-Antwort muss gegen ein festes JSON-Schema validiert werden.
- Header-Mismatch erzeugt Warnungen, aber keinen harten Fehler, solange `featureId` gueltig ist.

## UI Impact

- Living Sync zeigt den Import als eigenen Ereignistyp.
- Workspace und Cockpit lesen den letzten Snapshot fuer das aktive Feature.
- Feature-Graph nutzt `derivedStatus` als sichtbaren Realisierungsstand.
- Offene Punkte und Abweichungen werden am Feature sichtbar, ohne den Wizard-Graph strukturell umzubauen.
