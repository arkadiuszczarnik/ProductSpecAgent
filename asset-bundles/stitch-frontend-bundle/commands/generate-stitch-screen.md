---
name: generate-stitch-screen
description: Generates a new screen scaffold optimized for Stitch import.
---

Erzeuge eine neue Screen-Komponente unter `frontend/src/app/<route>/page.tsx`,
vorbereitet für einen Stitch-Export:

1. Lege `page.tsx` an mit einer Server Component, die nur `<StitchScreen />` rendert.
2. Lege `StitchScreen.tsx` als Client Component daneben an, mit klar markierten Slots:
   - `<!-- STITCH:HEADER -->`
   - `<!-- STITCH:CONTENT -->`
   - `<!-- STITCH:FOOTER -->`
3. Nutze Tailwind, keine Inline-Styles.
4. Kommentar oben in der Datei: „Generated for Stitch import — replace slots with exported markup."
