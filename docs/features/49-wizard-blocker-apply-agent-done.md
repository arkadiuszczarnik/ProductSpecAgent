# Wizard Blocker Apply Agent - Done

## Summary

Implemented an apply-focused wizard agent path for answered Decisions and Clarifications. When all blockers on the current step are answered, clicking **Weiter** now applies those answers into the current step's `wizard.json` fields and advances without calling the marker-producing completion agent.

## Validation

- `cd backend && ./gradlew test`
- `cd frontend && npm run lint`

## Deviations

- No diff preview UI was added, matching Variant A.
- Field validation is backend-owned and uses persisted wizard field keys.

## Follow-Up

- Complex `FEATURES` graph edits should get additional focused tests before allowing structural graph rewrites.
