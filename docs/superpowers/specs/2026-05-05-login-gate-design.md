# Design Spec — Login-Gate (Feature 42)

**Datum:** 2026-05-05
**Status:** Approved (User confirmed sections 1–4 in brainstorming)
**Feature-Doc:** [`docs/features/42-login-gate.md`](../../features/42-login-gate.md)

## Problem

Die Anwendung hat heute keinerlei Zugangsschutz: `SecurityConfig.kt:18-22` setzt `permitAll()` auf `/api/v1/**`, das Frontend hat keine Login-Seiten und kein Auth-Concept. Sobald die App nicht nur lokal läuft, ist alles offen — Projekte, Spec-Files, Decisions, alles. Wir brauchen ein einfaches Login-Gate vor jeder Aktion.

## Ziel

Minimal-invasives Login-Gate mit Selbst-Registrierung. Keine Mitgliedschaften, keine Rollen, keine Email-Verifikation, kein Password-Reset, keine Refresh-Tokens. Eingeloggte User teilen einen gemeinsamen Workspace (alle sehen alle Projekte). YAGNI strikt durchziehen.

## Scope-Entscheidungen aus dem Brainstorming

| Frage | Entscheidung |
|---|---|
| Mitglieder-/Rollenmodell | Nein — gemeinsamer Workspace (Modell A) |
| Token-Transport | httpOnly-Session-Cookie (Modell B) |
| Login-Identifier | Email |
| Token-Strategie | Ein einziges Cookie, 7 Tage fix, kein Refresh |
| Email-Verifikation | Nein |
| Password-Reset | Nein |
| Account-Löschung / Profil | Nein |
| Bestandsdaten-Migration | Keine — bleiben sichtbar für alle eingeloggten User |

## Architektur — High-Level-Flow

```
Browser ──▶ Next.js Proxy ──▶ Spring Boot Backend
   │            │                       │
   │     proxy.ts prüft Cookie    SecurityFilterChain
   │     → redirect /login        → /api/v1/auth/** permitAll
   │     wenn Cookie fehlt        → alles andere authenticated()
   │                              → JwtAuthenticationFilter (Cookie → SecurityContext)
   │
   └─ Cookie "session" (httpOnly, Secure, SameSite=Lax, 7d)
      Inhalt: signiertes JWT { sub: userId, email, exp }
```

**Daten-Flow Login:**
1. User → `POST /api/v1/auth/login` mit `{email, password}`
2. Backend prüft BCrypt-Hash, signiert JWT (HMAC-SHA256 + ENV-Secret), antwortet mit `Set-Cookie: session=…; HttpOnly; Secure; SameSite=Lax; Max-Age=604800`
3. Frontend redirected nach `searchParams.get('next') ?? '/'`
4. Folge-Requests senden Cookie automatisch
5. Logout: `POST /api/v1/auth/logout` setzt Cookie auf `Max-Age=0`

**Optimistic Auth Check** im Proxy: prüft nur Cookie-*Existenz*. Echte JWT-Validierung passiert ausschließlich im Backend.

## Backend — Komponenten

Neue Dateien unter `backend/src/main/kotlin/com/agentwork/productspecagent/`:

```
auth/
  User.kt                       # data class (id, email, passwordHash, createdAt)
  UserStorage.kt                # JSON-Persistenz unter data/users/{id}.json + email-Index
  UserService.kt                # register(email, pw), authenticate(email, pw)
  JwtService.kt                 # sign(userId, email): String, parse(token): JwtPayload?
  AuthCookieService.kt          # set/clear httpOnly-Cookie zentral
  AuthController.kt             # POST /auth/{register,login,logout}, GET /auth/me
  JwtAuthenticationFilter.kt    # OncePerRequestFilter: Cookie → SecurityContext
  AuthExceptions.kt             # InvalidCredentials, EmailAlreadyExists
config/
  SecurityConfig.kt             # geändert (siehe unten)
```

**Lib-Wahl:** `io.jsonwebtoken:jjwt-api/jjwt-impl/jjwt-jackson` (Standard, knapp). `oauth2ResourceServer().jwt()` mit `NimbusJwtDecoder.withSecretKey()` wäre eleganter, aber für selbst-signierte Tokens unnötig komplex.

**SecurityConfig (überarbeitet):**
```kotlin
http
  .cors {}
  .csrf { it.disable() }
  .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
  .authorizeHttpRequests {
    it.requestMatchers("/api/health", "/api/v1/auth/**").permitAll()
      .anyRequest().authenticated()
  }
  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
  .exceptionHandling {
    it.authenticationEntryPoint { _, res, _ -> res.status = 401 }
  }
```

**CORS:** Bestehender `CorsConfig.kt` ist bereits korrekt (`allowCredentials = true`, `allowedOrigins` aus `cors.allowedOrigins`-Properties). Keine Code-Änderung — nur sicherstellen, dass `cors.allowedOrigins` in `application-dev.yml` (z. B. `http://localhost:3000`) und in der prod-Konfiguration gesetzt ist (kein `*` möglich, weil `allowCredentials = true`).

## Backend — Endpoints

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/v1/auth/register` | `{email, password}` | 201 + `Set-Cookie` + `{userId, email}` |
| POST | `/api/v1/auth/login` | `{email, password}` | 200 + `Set-Cookie` + `{userId, email}` |
| POST | `/api/v1/auth/logout` | — | 204 + `Set-Cookie: session=; Max-Age=0` |
| GET | `/api/v1/auth/me` | — (Cookie) | 200 `{userId, email}` oder 401 |

**Error-Mapping:**
- `400` — ungültiges Email-Format / Password zu kurz / Body fehlt
- `401` — falsches Passwort, ungültiges/abgelaufenes Cookie
- `409` — Email schon registriert
- Body: `{error: "INVALID_CREDENTIALS", message: "Login fehlgeschlagen"}` — generisch (gegen User-Enumeration)

## Backend — Datenmodell

```kotlin
@Serializable
data class User(
    val id: String,            // UUIDv4
    val email: String,         // lowercase, eindeutig
    val passwordHash: String,  // BCrypt, Strength 10
    val createdAt: String      // ISO-8601 UTC
)
```

**Storage**: `UserStorage` analog zu bestehenden `*Storage`-Klassen (`java.nio.file`-basiert).

```
data/
  users/
    {user-id}.json
    _index/
      email-{lowercase-email}.json   # Pointer-File mit { userId } für O(1) Email-Lookup
```

Email-Index als Pointer-File (statt separater Map-Datei) ist konsistent mit dem No-DB-Konzept und vermeidet Locking. Doppelte Email → `Files.exists` auf Index-Pfad vor `createNewFile`.

## Backend — Konfiguration

`application.yml`:
```yaml
auth:
  jwt:
    secret: ${AUTH_JWT_SECRET:}     # Pflicht in prod, fail-fast bei leer
    expiry: 604800                  # 7 Tage in Sekunden
  cookie:
    name: session
    secure: ${AUTH_COOKIE_SECURE:true}
```

`application-dev.yml`: Default-Secret + `AUTH_COOKIE_SECURE: false` (für lokales http).

`docker-compose.yml`: `AUTH_JWT_SECRET` als ENV ergänzen (Pflicht). `AUTH_COOKIE_SECURE=false` für lokales Setup.

**Password-Regeln (NIST 800-63B-konform):** min 8, max 128 Zeichen. Keine Komplexitäts-Voodoo. BCrypt-Strength 10.

## Frontend — Komponenten

Neue Dateien unter `frontend/src/`:

```
proxy.ts                              # NEU — replaces middleware
app/(auth)/login/page.tsx
app/(auth)/register/page.tsx
app/(auth)/layout.tsx                 # leeres Layout ohne Sidebar
components/auth/LoginForm.tsx         # client component, react-hook-form + zod
components/auth/RegisterForm.tsx
components/auth/LogoutButton.tsx
lib/auth/api.ts                       # login/register/logout/me
lib/stores/auth-store.ts              # zustand: { user, status: 'loading'|'auth'|'guest' }
```

**Geänderte Dateien:**
- `lib/api.ts` — `apiFetch` um `credentials: 'include'` erweitern; bei `401` → `auth-store.clear()` + `router.push('/login')`
- `app/layout.tsx` — `GET /auth/me` beim Mount via Client-Provider, Store initialisieren (verhindert Flash auf `/login` bei Reload)
- `components/layout/AppShell.tsx` — Logout-Button + User-Email-Anzeige im Shell-Header einbauen

## Frontend — `proxy.ts`

```ts
import { NextResponse, NextRequest } from 'next/server'

const PUBLIC = ['/login', '/register']

export default function proxy(req: NextRequest) {
  const path = req.nextUrl.pathname
  const isPublic = PUBLIC.some(p => path.startsWith(p))
  const hasCookie = req.cookies.has('session')

  if (!isPublic && !hasCookie) {
    const url = new URL('/login', req.nextUrl)
    url.searchParams.set('next', path)
    return NextResponse.redirect(url)
  }
  if (isPublic && hasCookie) {
    return NextResponse.redirect(new URL('/', req.nextUrl))
  }
  return NextResponse.next()
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
}
```

## Frontend — Verhalten

**Login-Page:** shadcn `Form` + `Input` + `Button`, Felder email + password. Submit → `POST /auth/login` (`credentials: 'include'`) → Erfolg: `router.replace(searchParams.get('next') ?? '/')`. Fehler: Toast „Login fehlgeschlagen" (kein Detail). Link „Noch kein Konto? Registrieren →".

**Register-Page:** identischer Flow gegen `/auth/register`. Bei Erfolg ist Cookie gesetzt → redirect zu `/`.

**Logout:** `POST /auth/logout` → `auth-store.clear()` → `router.replace('/login')`.

## Testing

**Backend (Pflicht, folgen `@TempDir`-Pattern):**

| Test | Was |
|---|---|
| `UserStorageTest` | save/load, email-Index, doppelte Email, fehlende ID |
| `JwtServiceTest` | sign+parse roundtrip, abgelaufenes/manipuliertes Token, falscher Secret |
| `UserServiceTest` | register validiert, BCrypt-Hash, authenticate falsch/richtig |
| `AuthControllerTest` (MockMvc) | login Cookie+200, logout cleart, register dupliziert→409, falsches PW→401 generisch, `/auth/me` ohne→401, mit→200 |
| `JwtAuthenticationFilterTest` | Cookie+gültig→Context gesetzt, fehlend→leer, abgelaufen→leer (kein Throw) |
| `SecurityIntegrationTest` | `/api/v1/projects` ohne Cookie→401, mit gültigem→200 |

**Frontend:** Manuelle Browser-Verifikation (kein Test-Runner konfiguriert; matcht bestehende Features).

## Akzeptanzkriterien

1. Frischer Start ohne User: Aufruf `/` → automatisch redirect zu `/login`.
2. Über `/register` Konto anlegen → automatisch eingeloggt → Projekte sichtbar.
3. Logout → zurück auf `/login`, alle weiteren Aufrufe redirecten.
4. Reload nach Login → bleibt eingeloggt, kein Flash auf `/login` (initiales `GET /auth/me` im Layout).
5. Cookie manuell gelöscht → nächster API-Call gibt 401, FE redirected zu `/login`.
6. Backend: ohne gültiges Cookie liefern alle `/api/v1/projects/**`-Endpoints 401.
7. Doppelte Registrierung → 409, Toast „Email bereits registriert".
8. `application.yml` ohne `AUTH_JWT_SECRET` (Profile prod) → App startet nicht (fail-fast).

## Out of Scope

- Mitglieder-/Rollenmodell, `members.json`, `OWNER`/`CONTRIBUTOR`/`VIEWER`
- Refresh-Tokens, `/auth/refresh`, Sliding Expiration
- Email-Verifikation, Password-Reset, Account-Löschung, Profilseite, Admin-UI
- Per-User-Sicht auf Projekte (`ownerUserId` an Projekten)
- Frontend-Unit-Tests
- OAuth/SSO/SAML
- Rate-Limiting auf Login (kann später als separates Feature)
- User-Enumeration über Timing-Side-Channels (nicht-Ziel; pragmatischer Schutz reicht)

## Auswirkungen auf bestehende Docs

- `docs/architecture/auth.md` — komplett überschreiben: Rollen-Tabelle raus, Bearer→Cookie, Refresh raus, Members raus
- `docs/features/00-feature-set-overview.md` — Zeile `| 42 | Login-Gate | [42-login-gate.md](42-login-gate.md) | Feature 0 | M |` ergänzen
