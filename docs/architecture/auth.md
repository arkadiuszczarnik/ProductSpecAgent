# Architecture: Authentifizierung

> **Stand: 2026-05-05** — überarbeitet im Rahmen von [Feature 42 — Login-Gate](../features/42-login-gate.md). Frühere Versionen beschrieben ein Member-/Rollenmodell mit Refresh-Tokens; das wurde zugunsten YAGNI auf das Minimum reduziert.

## Überblick

Login-Gate vor jeder Aktion via Spring Security + selbst-signiertem JWT in einem httpOnly-Session-Cookie. Eingeloggte User teilen einen gemeinsamen Workspace — keine Mitglieder, keine Rollen, keine Per-User-Projekt-Ownership.

## Flow

1. User registriert sich via `POST /api/v1/auth/register` (`{email, password}`)
2. User loggt sich ein via `POST /api/v1/auth/login` (`{email, password}`)
3. Backend validiert BCrypt-Hash, signiert JWT (HMAC-SHA256, ENV-Secret) und setzt es als Cookie:
   `Set-Cookie: session=<jwt>; HttpOnly; Secure; SameSite=Lax; Max-Age=604800; Path=/`
4. Browser sendet das Cookie automatisch bei jedem Folge-Request
5. Backend (JwtAuthenticationFilter) liest Cookie, validiert JWT, setzt SecurityContext
6. Logout via `POST /api/v1/auth/logout` setzt das Cookie auf `Max-Age=0`

## Token-Strategie

- **Ein einziges Cookie**, 7 Tage gültig, fix (kein Sliding, kein Refresh-Token)
- Bei Ablauf: User loggt sich neu ein
- Bei Logout-Wunsch von einem fremden Gerät: keine Server-Side-Invalidation in dieser Iteration (akzeptiertes Risiko bei interner Nutzung)

## Persistenz

- **User**: JSON-Files unter `data/users/{user-id}.json`
- **Email-Index**: Pointer-Files unter `data/users/_index/email-{lowercase-email}.json` mit `{ userId }` — O(1) Email-Lookup ohne Map-Datei
- **Passwörter**: BCrypt mit Strength 10
- **Keine** `members.json` pro Projekt (kein Member-Modell in dieser Iteration)

## Endpoints

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/v1/auth/register` | `{email, password}` | 201 + `Set-Cookie` + `{userId, email}` |
| POST | `/api/v1/auth/login` | `{email, password}` | 200 + `Set-Cookie` + `{userId, email}` |
| POST | `/api/v1/auth/logout` | — | 204 + `Set-Cookie: session=; Max-Age=0` |
| GET | `/api/v1/auth/me` | — | 200 `{userId, email}` oder 401 |

Error-Bodies generisch (`INVALID_CREDENTIALS`, `EMAIL_ALREADY_EXISTS`, `VALIDATION_ERROR`) — kein Hinweis auf Email-Existenz (User-Enumeration).

## Spring Security Setup

- Stateless Sessions (`SessionCreationPolicy.STATELESS`)
- `SecurityFilterChain`:
  - `permitAll()` für `/api/health` und `/api/v1/auth/**`
  - `authenticated()` für `anyRequest()`
  - `JwtAuthenticationFilter` vor `UsernamePasswordAuthenticationFilter`
  - `AuthenticationEntryPoint` setzt 401 ohne Body-Redirect (JSON-API)
- `BCryptPasswordEncoder(strength = 10)`
- CORS mit `allowCredentials = true` und konkreter Frontend-Origin (kein `*`)

## Frontend-Auth (Next.js 16 App Router)

- `proxy.ts` prüft optimistisch Cookie-Existenz, redirected zu `/login` bei fehlendem Cookie
- Public Routes: `/login`, `/register`
- `lib/api.ts` setzt `credentials: 'include'`, globaler 401-Handler clear-t Auth-Store + redirected
- `auth-store` (Zustand) initialisiert via `GET /auth/me` im Root-Layout (verhindert Flash auf `/login` bei Reload)

## Konfiguration

```yaml
auth:
  jwt:
    secret: ${AUTH_JWT_SECRET:}        # Pflicht in prod, fail-fast bei leer
    expiry: 604800                     # 7 Tage
  cookie:
    name: session
    secure: ${AUTH_COOKIE_SECURE:true} # in dev: false (lokales http)
```

`AUTH_JWT_SECRET` muss eine HMAC-taugliche Zeichenkette sein (≥ 32 Bytes). Beispiel-Generierung: `openssl rand -base64 48`.

## Password-Regeln

NIST 800-63B-konform:
- Minimum 8 Zeichen
- Maximum 128 Zeichen
- Keine Komplexitätsregeln (Großbuchstaben/Zahlen/Sonderzeichen *nicht* erzwungen — gilt als kontraproduktiv)

## Bewusst nicht enthalten

- Rollen (`OWNER`, `CONTRIBUTOR`, `VIEWER`) und Mitgliedschaften
- Refresh-Tokens, Sliding Expiration, Server-Side Token-Invalidation
- Email-Verifikation, Password-Reset, Account-Löschung
- OAuth/SSO/SAML
- Rate-Limiting auf `/auth/login`

Diese Punkte können als spätere Features ergänzt werden, sobald die Anwendungsfälle eintreten.
