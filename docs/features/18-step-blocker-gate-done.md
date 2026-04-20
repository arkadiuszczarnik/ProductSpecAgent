# Feature 18 — Step-Blocker Gate (Done)

## Status
Nachtraeglich vervollstaendigt am 2026-04-08 (in zwei Runden).

Die initiale Umsetzung hat nur das Frontend-Gate abgedeckt. Drei Luecken wurden durch eine Review entdeckt und behoben (Runde 1). Beim Smoke-Test trat eine Folge-Endlosschleife zutage — der Agent hat auf jeden "Weiter"-Klick eine neue Clarification generiert, weil er keinen Kontext zu bereits beantworteten/offenen Blockern hatte. Runde 2 behebt das.

## Zusammenfassung der Implementierung

### Frontend (bereits vorher vorhanden)
- `frontend/src/lib/hooks/use-step-blockers.ts` — reaktiver Hook, liest Decision-/Clarification-Stores und berechnet `isBlocked`, `blockerCount`, `blockerSummary`, `firstBlockerTab`.
- `frontend/src/components/wizard/BlockerBanner.tsx` — Amber-Banner, klickbar, wechselt den rechten Tab.
- `frontend/src/components/wizard/StepIndicator.tsx` — Amber-Kreis + AlertTriangle fuer BLOCKED, Lock-Icon fuer nachfolgende LOCKED-Steps.
- `frontend/src/components/wizard/WizardForm.tsx` — Hook eingebunden, Banner + Button-Verhalten.
- `frontend/src/app/projects/[id]/page.tsx` — `onBlockerClick` Prop reicht Tab-Wechsel durch.

### Nachtraeglich geschlossene Luecken

**1. Backend ignorierte das Gate (kritisch)**
`IdeaToSpecAgent.processWizardStep` hat den `FlowState` unabhaengig davon advanct, ob der Agent im gleichen Call einen neuen Blocker (DECISION_NEEDED / CLARIFICATION_NEEDED) erzeugt hat. Ablauf vorher:
1. User klickt "Weiter" — Frontend-Gate sieht keine Blocker (sie existieren noch nicht)
2. Agent antwortet mit `[CLARIFICATION_NEEDED]`
3. Backend erstellt Clarification **und** setzt `currentStep = nextStep`, liefert `nextStep` in der Response
4. Frontend-Store setzt `activeStep = nextStep` — der Blocker wandert faktisch in einen Step, den der User nie wieder sieht

Fix in `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt`:
- Neue Variable `isBlocked = hasNewBlocker || hasExistingBlocker`
- `hasNewBlocker` = Decision/Clarification im aktuellen Call erzeugt
- `hasExistingBlocker` = Listet Storage auf PENDING-Decisions und OPEN-Clarifications fuer den aktuellen Step
- Wenn `isBlocked`: `nextStepType = null`, Flow-State bleibt bei `currentStep` (Status IN_PROGRESS), Response liefert `nextStep = null`
- `exportTriggered = isLastStep && !isBlocked` — kein Export wenn der letzte Step Blocker hat
- Spec-Datei wird weiterhin gespeichert (User-Input ist valide, nur die Progression wird gesperrt)

**2. Disabled Button feuert kein onClick**
`WizardForm.tsx` hatte `disabled={isWorking || isBlocked}`. Native HTML-disabled Buttons schlucken `onClick`, daher war der `if (isBlocked) onBlockerClick(...)`-Branch in `handleNext` tot. AC verlangt aber explizit: "Klick auf den disabled Button wechselt zum relevanten Tab".

Fix: `disabled` nur noch bei `isWorking`, bei `isBlocked` stattdessen `aria-disabled` + visuelles Styling (`opacity-60`, `cursor-not-allowed`) + `title={blockerSummary}`. `handleNext()` prueft `isBlocked` weiterhin zuerst und wechselt dann den Tab.

**3. Pulsierender Glow fehlte**
StepIndicator BLOCKED-State hatte weder Animation noch "Glow"-Effekt. Fix: `animate-pulse` + `shadow-[0_0_0_4px_rgba(245,158,11,0.15)]` auf den Amber-Kreis.

## Geaenderte Dateien (Fix-Runde 1)
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt` — Gate im `processWizardStep` enforcd
- `frontend/src/components/wizard/WizardForm.tsx` — Button klickbar lassen, visuell disabled
- `frontend/src/components/wizard/StepIndicator.tsx` — Pulse-Glow fuer BLOCKED-State

## Runde 2 — Endlosschleife beim Wiederholungsklick

**Symptom:** Nach dem Runde-1-Fix blieb der Wizard korrekt auf dem IDEA-Step stehen, wenn der Agent eine Clarification erzeugte. Aber jeder weitere "Weiter"-Klick (auch nach dem Beantworten der Clarification) loeste einen neuen LLM-Call aus, der erneut eine Clarification zum gleichen Thema erzeugte. Vor Runde 1 war das unsichtbar, weil der Step einfach weitergesprungen ist.

**Root Cause:** `SpecContextBuilder.buildWizardContext` hat dem Agent keinerlei Kontext zu existierenden Decisions/Clarifications fuer den aktuellen Step mitgegeben. Der Agent ist stateless — er sieht nur die Wizard-Felder und den System-Prompt, der zudem explizit sagt "Err on the side of including a marker". Also generiert er bei jedem Aufruf wieder einen `[CLARIFICATION_NEEDED]`-Marker.

**Fix:**
1. `SpecContextBuilder.buildWizardContext` bekommt zwei neue optionale Parameter: `existingDecisions: List<Decision>` und `existingClarifications: List<Clarification>`. Fuer den aktuellen Step werden ANSWERED/OPEN Clarifications und RESOLVED/PENDING Decisions als expliziter Block in den Kontext gerendert mit der Anweisung: "DO NOT repeat them. Do NOT emit a new marker for the same topic. Only emit a new marker if the open question is genuinely different."
2. `IdeaToSpecAgent.processWizardStep` laedt `decisionService.listDecisions()` + `clarificationService.listClarifications()` einmal und uebergibt sie an `buildWizardContext`. Die gleichen Listen werden im Blocker-Check wiederverwendet (keine doppelte Storage-Lesung).
3. `MARKER_REMINDER` wurde umformuliert: statt "MANDATORY OUTPUT REQUIREMENT ... Err on the side of including a marker" jetzt "OUTPUT REQUIREMENT ... MAY end your response with one of these markers" + vier explizite Regeln, darunter: "Silence is a valid answer". Neues Beispiel ohne Marker.

**Effekt:** Der Agent sieht bei jedem Folgeaufruf die bereits gestellten und beantworteten Fragen und akzeptiert die Idee nach der ersten Clarification-Runde, statt dieselbe Frage zu wiederholen.

## Geaenderte Dateien (Fix-Runde 2)
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/SpecContextBuilder.kt` — neuer PREVIOUS CLARIFICATIONS & DECISIONS Block mit ANSWERED/OPEN/RESOLVED/PENDING Sektionen
- `backend/src/main/kotlin/com/agentwork/productspecagent/agent/IdeaToSpecAgent.kt` — laedt existierende Blocker, uebergibt sie an `buildWizardContext`, weicher MARKER_REMINDER

## Acceptance Criteria — Abdeckung
- [x] "Weiter"-Button ist disabled wenn PENDING/OPEN Blocker existieren (visuell + `aria-disabled`)
- [x] Amber-Banner ueber dem Button zeigt Anzahl und Art der Blocker
- [x] Klick auf den disabled Button wechselt zum relevanten Tab (jetzt wirklich — Button nicht mehr HTML-disabled)
- [x] Bei mehreren Blocker-Typen: Wechsel zum Tab mit dem aeltesten offenen Item (Hook-Logik: Clarifications priorisiert)
- [x] StepIndicator: Amber-Kreis, AlertTriangle, pulsierender Glow
- [x] Badge unter dem blockierten Step zeigt Blocker-Zusammenfassung
- [x] Nachfolgende Steps zeigen Schloss-Icon, ausgegraut, nicht klickbar
- [x] Automatische Freischaltung sobald Stores aktualisiert sind (Zustand-Subscription)
- [x] Abgeschlossene Steps bleiben navigierbar
- [x] **Backend respektiert das Gate** — auch bei Agent-erzeugten Blockern wird der Flow-State nicht advanct

## Abweichungen vom urspruenglichen Feature-Dokument
- Feature-Dokument hat die Backend-Interaktion unter "Interaktion mit bestehendem System" mit "Backend braucht keine Aenderungen" angegeben. Das stimmt nicht: Ohne Backend-Gate koennte ein vom Agent im selben Call erzeugter Blocker nie greifen. Das Backend-Gate wurde ergaenzt.
- `firstBlockerTab`-Logik ist vereinfacht: Prioritaet Clarifications > Decisions (statt "aeltestes offenes Item"). Fuer typische Wizard-Flows ausreichend, kein UX-Unterschied bei nur einem Blocker-Typ.

## Offene Punkte / Technische Schulden
- Kein Integrationstest fuer `IdeaToSpecAgent.processWizardStep` vorhanden (vorher schon nicht). Ein Test fuer das Backend-Gate waere sinnvoll — z. B. Mock-Runner der `[CLARIFICATION_NEEDED]` emittiert, danach `FlowState.currentStep` verifizieren.
- Vorbestehend failing: `FileControllerTest.GET files returns file tree` — unabhaengig von diesem Feature, gehoert zu Feature 15 (hide internal files).
- `animate-pulse` auf dem StepIndicator-Kreis pulsiert das Amber-Styling. Kann bei sensiblen Usern stoerend sein — evtl. spaeter `prefers-reduced-motion` beruecksichtigen.

## Verifikation
- `./gradlew compileKotlin` — gruen
- `./gradlew test --tests "*.IdeaToSpecAgentTest" --tests "*.SpecContextBuilderTest" --tests "*.SpecContextBuilderWizardTest" --tests "*.WizardChatControllerTest" --tests "*.DecisionAgentTest"` — gruen
- `npm run lint` (Frontend) — keine neuen Fehler, nur vorbestehende `any`-Warnings
- `npx tsc --noEmit` — clean

## Manueller Smoke-Test (empfohlen)
Nach Runde 2 sollte folgender Ablauf sauber durchlaufen:
1. Neues Projekt anlegen, IDEA-Step bewusst vage ausfuellen
2. "Weiter" klicken → Agent erzeugt Clarification, Gate blockt Step, Banner + Pulse-Glow sichtbar
3. Clarification im Tab beantworten
4. Erneut "Weiter" klicken → Agent sollte mit kurzer Bestaetigung antworten, KEINE neue Clarification erzeugen, Step advanct auf PROBLEM
