# Feature 35 — Feature-Proposal nutzt Upload-Dokumente

**Phase:** Spec-Quality
**Abhängig von:** Feature 22 (Features-Graph-Wizard, done), Feature 28 (Project Document Upload, done)
**Aufwand:** S
**Design-Spec:** [`docs/superpowers/specs/2026-04-30-feature-proposal-with-uploads-design.md`](../superpowers/specs/2026-04-30-feature-proposal-with-uploads-design.md)

## Problem

Der `FeatureProposalAgent` (ausgelöst durch den "Vorschlagen"-Button im FEATURES-Wizard-Step) baut seinen Prompt heute nur aus den Spec-Dateien `idea.md`, `problem.md`, `target_audience.md`, `mvp.md` und der `Category`. Vom Nutzer hochgeladene Markdown- und Text-Dokumente aus dem "Documents"-Tab — also genau die Stelle, an der Anwender konkrete Feature-Hinweise (Konkurrenz-Specs, Kunden-Briefings, Architektur-Notizen) hinterlegen — fließen nicht in den Vorschlag ein.

## Ziel

Vor dem LLM-Call lädt ein neuer `UploadPromptBuilder` alle MD-/Plain-Text-Uploads des Projekts aus `UploadStorage` (`projects/{projectId}/docs/uploads/`), sanitiert die Inhalte gegen Prompt-Injection und hängt sie als deutlich abgegrenzte Referenz-Sektion an den User-Prompt. PDFs werden still übersprungen. Der Endpoint-Vertrag und der Rückgabetyp `WizardFeatureGraph` ändern sich nicht.

## Architektur

Siehe Design-Spec für das vollständige Bild. Kurzfassung:

- **Neu:** `agent/UploadPromptBuilder.kt` (Spring `@Component`).
- **Geändert:** `agent/FeatureProposalAgent.kt` zieht den Builder zusätzlich; System-Prompt verlängert um einen Anti-Injection-Satz.
- **Geändert:** `storage/UploadStorage.kt` bekommt eine `readById(projectId, docId)`-Methode.
- **Neu:** `@ConfigurationProperties("feature-proposal.uploads")` mit `maxBytesPerFile = 100 KB`, `maxBytesTotal = 500 KB`.
- **Frontend:** Helper-Text-Zeile unter dem "Vorschlagen"-Button: *"Berücksichtigt Markdown- und Text-Dateien aus dem Documents-Tab."*

## Datenmodell

Keine Änderungen am Domain-Modell oder Persistenz-Format. `WizardFeatureGraph`, `Document`, `IndexEntry` bleiben unverändert.

## Service-Schnittstellen

```kotlin
// neu
@Component
class UploadPromptBuilder(
    private val uploadStorage: UploadStorage,
    private val props: FeatureProposalUploadsProperties,
) {
    fun renderUploadsSection(projectId: String): String
}

// erweitert
class UploadStorage {
    fun readById(projectId: String, docId: String): ByteArray
}
```

## Akzeptanzkriterien

1. `FeatureProposalAgent.proposeFeatures` ruft `UploadPromptBuilder.renderUploadsSection` auf und bettet den Output (falls nicht leer) zwischen Spec-Kontext und JSON-Format-Anweisung im User-Prompt ein.
2. MD- und TXT-Uploads erscheinen mit Marker-Format `--- BEGIN UPLOADED DOCUMENT: <title> (<mime>) --- … --- END UPLOADED DOCUMENT ---`.
3. PDFs werden still übersprungen (`log.debug`); kein UI-Fehler.
4. Per-File-Cap (100 KB Default) und Total-Cap (500 KB Default) greifen mit sichtbarem Truncation-Hinweis im Prompt.
5. Marker-Phrasen im Upload-Inhalt werden via Zero-Width-Space neutralisiert.
6. System-Prompt instruiert den LLM, Upload-Inhalt nicht als Anweisung zu interpretieren.
7. Projekte ohne MD/TXT-Uploads erzeugen einen bit-identischen Prompt zum Status quo.
8. `UploadStorage`-Fehler degradieren den Proposal-Endpoint nicht (Endpoint liefert weiterhin 200 ohne Upload-Sektion).
9. Frontend zeigt die Helper-Text-Zeile.
10. Konfig-Werte sind via `@ConfigurationProperties` + `@field:Positive` validiert.

## Betroffene Dateien

**Backend (neu):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/UploadPromptBuilder.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/config/FeatureProposalUploadsProperties.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/agent/UploadPromptBuilderTest.kt`

**Backend (erweitert):**
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgent.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UploadStorage.kt`
- `backend/src/main/resources/application.yml`
- `backend/src/test/kotlin/com/agentwork/productspecagent/agent/FeatureProposalAgentTest.kt`
- `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UploadStorageTest.kt`

**Frontend (erweitert):**
- `frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx` (Helper-Text unter dem "Vorschlagen"-Button um Zeile 158–173)

## YAGNI

- Keine PDF-Extraktion.
- Kein Token-genaues Budget (Bytes reichen).
- Kein Pro-Projekt-Override der Budgets.
- Keine UI-Badge mit Datei-Anzahl.
- Kein Feature-Toggle.
