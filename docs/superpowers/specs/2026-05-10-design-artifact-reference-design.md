# Design Artifact Reference Design

## Goal

The DESIGN step should persist its human-readable summary as a design artifact, not as a temporary step spec file. Final product specs should keep a stable reference to the design artifact when a design exists.

## Current Problem

`DesignWorkbenchController.complete()` writes the workbench summary to `spec/design.md`. Later, the final wizard step writes `spec/spec.md`. `ProjectService.saveSpecFile("spec.md", ...)` intentionally removes every other file under `spec/`, so `spec/design.md` disappears.

That cleanup is correct for step-spec files, but the generated design summary is no longer just a step-spec input. It belongs with the design output.

## Desired Behavior

- Completing the DESIGN step writes the summary to `design/design.md`.
- Completing the DESIGN step no longer writes `spec/design.md`.
- Export and handoff ZIPs include `design/design.md` whenever the DESIGN step has been completed.
- The final `spec/spec.md` includes a concise Design section when a design summary exists.
- The Design section references `design/design.md` and, when applicable, `design/screens/design/index.html`.
- The final spec does not duplicate the full design summary.

## Data Flow

1. User generates a design in the workbench.
2. User completes the DESIGN step.
3. Backend writes:
   - active preview HTML to `design/screens/design/index.html`
   - design summary to `design/design.md`
   - DESIGN wizard step data for progression/context
4. Later final step completion builds `spec/spec.md`.
5. Before saving `spec/spec.md`, the summary prompt includes a mandatory instruction to link existing design artifacts.
6. Saving `spec/spec.md` may still clean `spec/*`, but it does not touch `design/*`.

## Boundaries

- Do not change the existing `spec/spec.md` cleanup semantics.
- Do not reintroduce per-step files under `spec/`.
- Do not duplicate the whole design summary into the final spec.
- Keep the design artifact exported alongside active design screen files.

## Verification

- Controller test proves DESIGN completion writes `design/design.md` and does not create `spec/design.md`.
- Wizard completion test proves final spec prompt includes a design artifact reference instruction when a design summary exists.
- Export and handoff tests prove `design/design.md` is included with the design screen.
