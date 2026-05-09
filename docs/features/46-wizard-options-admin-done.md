# Feature 46 Done: Wizard Options Admin

## Implemented

- Backend catalog domain, default catalog, object-store persistence, validation, reset, and admin-role protection.
- Public and admin API endpoints for wizard options.
- Frontend catalog client and catalog-aware wizard option helpers.
- Admin page at `/admin/wizard-options`.
- Wizard dropdowns, feature scope selection, and asset-bundle missing coverage now read from the catalog.

## Verification

- `cd backend && ./gradlew test`
- `cd frontend && npx eslint ...`
- `cd frontend && npx tsc --noEmit`
- `cd frontend && npm run build`

## Notes

- Defaults mirror the previous hard-coded frontend options.
- Disabled options remain stored but are hidden from wizard dropdowns and missing asset-bundle calculations.
- Admin catalog changes require `ROLE_ADMIN`; configure admin emails with `auth.admin-emails` / `AUTH_ADMIN_EMAILS`.
