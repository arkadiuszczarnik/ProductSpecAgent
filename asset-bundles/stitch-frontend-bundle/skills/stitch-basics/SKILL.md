---
name: stitch-basics
description: Use when generating UI from a Stitch design export or when bridging Stitch output into the project's component library.
---

# Stitch Basics

Google Stitch produziert HTML/CSS bzw. React-Snippets aus Mockups oder Prompts.
Dieses Skill erklärt den Übergabeschritt zwischen Stitch-Export und unserem Frontend.

## Vorgehen
1. Im Stitch-Workspace „Export → Code" wählen (React + Tailwind bevorzugt).
2. Erzeugte Komponente in `frontend/src/components/generated/` ablegen.
3. Stitch-spezifische Inline-Styles durch Tailwind-Klassen oder shadcn/ui-Primitives ersetzen.
4. Prop-Interface extrahieren und an die Domain-Typen aus `lib/api.ts` koppeln.

## Was nicht zu tun ist
- Stitch-Export nicht 1:1 in `app/` einchecken — er kennt unsere Routing-Konventionen nicht.
- Keine Tokens/Farben aus dem Export übernehmen, ohne sie gegen unsere Tailwind-Theme-Tokens zu spiegeln.
