# Login-Gate (Feature 42) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Login-Gate vor jeder API-/UI-Aktion: Selbst-Registrierung + Login mit JWT in httpOnly-Cookie. Eingeloggte User teilen einen gemeinsamen Workspace.

**Architecture:** Spring Security stateless mit selbst-signiertem JWT (HMAC-SHA256, 7d), übertragen via httpOnly-Session-Cookie. Persistenz via `ObjectStore` (Pattern aus Feature 31). Frontend-Proxy prüft Cookie-Existenz und redirected zu `/login`; echte Validierung im Backend-Filter.

**Tech Stack:** Kotlin 2.3 + Spring Boot 4 + Spring Security 6 + jjwt 0.12 + BCrypt + Next.js 16 (App Router, Proxy) + Zustand + shadcn/ui.

---

## Anpassungen gegenüber Design-Spec

- **Storage:** Spec sagt `data/users/...` (java.nio.file). Projekt-Pattern ist `ObjectStore` (Feature 31 done). Plan nutzt `ObjectStore`-Keys `users/{userId}.json` + `users/_index/email-{email}.json`. Tests inherit `S3TestSupport` mit MinIO-Testcontainer.
- **Packages:** Spec listete alles unter `auth/`. Plan folgt Projektkonvention: `domain/User.kt`, `storage/UserStorage.kt`, `service/UserService.kt` + `service/AuthExceptions.kt`, `auth/{JwtService, AuthCookieService, JwtAuthenticationFilter}.kt`, `api/AuthController.kt`. Auth-Errors gehen in `GlobalExceptionHandler` (zentrale Konvention).
- **Frontend-API:** Auth-Helpers (`login`, `register`, `logout`, `me`) gehen in `lib/api.ts` (Konvention: keine fetch-Aufrufe ausserhalb).

## File Structure

**Backend — neu:**
- `domain/User.kt` — `@Serializable data class User(id, email, passwordHash, createdAt)`
- `storage/UserStorage.kt` — ObjectStore-basierte Persistenz mit Email-Index
- `service/UserService.kt` — `register(email, pw)`, `authenticate(email, pw)`
- `service/AuthExceptions.kt` — `InvalidCredentialsException`, `EmailAlreadyExistsException`, `WeakPasswordException`, `InvalidEmailException`
- `auth/JwtService.kt` — `sign(userId, email): String`, `parse(token): JwtPayload?`
- `auth/AuthCookieService.kt` — `setSessionCookie(res, token)`, `clearSessionCookie(res)`
- `auth/JwtAuthenticationFilter.kt` — `OncePerRequestFilter`, Cookie → `SecurityContext`
- `api/AuthController.kt` — `POST /auth/register`, `/auth/login`, `/auth/logout`, `GET /auth/me`
- `domain/AuthDtos.kt` — Request/Response-DTOs (`AuthCredentials`, `AuthMeResponse`)

**Backend — geändert:**
- `config/SecurityConfig.kt` — Filter-Chain mit `JwtAuthenticationFilter`, AuthEntryPoint, `permitAll` für `/auth/**`+`/api/health`
- `api/GlobalExceptionHandler.kt` — vier neue Handler für Auth-Exceptions
- `build.gradle.kts` — jjwt-Dependencies
- `src/main/resources/application.yml` — `auth.*`-Config + `cors.allowed-origins`-Verifikation
- `docker-compose.yml` — `AUTH_JWT_SECRET` + `AUTH_COOKIE_SECURE`

**Backend — Tests neu:**
- `storage/UserStorageTest.kt` (extends `S3TestSupport`)
- `auth/JwtServiceTest.kt` (Plain JUnit, kein Spring-Context)
- `service/UserServiceTest.kt` (extends `S3TestSupport`)
- `auth/JwtAuthenticationFilterTest.kt`
- `api/AuthControllerTest.kt` (`@SpringBootTest` + MockMvc + MinIO)
- `api/SecurityIntegrationTest.kt` (End-to-end: ohne Cookie → 401, mit → 200)

**Frontend — neu:**
- `proxy.ts` (auf Repo-Root-Ebene unter `frontend/src/`)
- `app/(auth)/layout.tsx`
- `app/(auth)/login/page.tsx`
- `app/(auth)/register/page.tsx`
- `components/auth/LoginForm.tsx`
- `components/auth/RegisterForm.tsx`
- `components/auth/LogoutButton.tsx`
- `components/auth/AuthProvider.tsx` — Client-Component, ruft `me()` beim Mount
- `lib/stores/auth-store.ts`

**Frontend — geändert:**
- `lib/api.ts` — `apiFetch` mit `credentials: 'include'`, 401-Handler, neue Auth-Helpers + Types
- `app/layout.tsx` — `AuthProvider` einbinden
- `components/layout/AppShell.tsx` — `LogoutButton` an Bottom der IconRail (über Settings)

**Docs — geändert:**
- bereits geschrieben in vorherigem Schritt (`auth.md`, Feature-Doc, Overview, Design-Spec)

---

## Tasks

### Task 1: jjwt-Dependency + auth-Config-Schema

**Files:**
- Modify: `backend/build.gradle.kts`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/config/AuthProperties.kt`

- [ ] **Step 1: jjwt-Dependencies ergänzen**

In `backend/build.gradle.kts` im `dependencies`-Block ergänzen (vor dem `// Test`-Block):

```kotlin
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

- [ ] **Step 2: `AuthProperties.kt` erstellen**

```kotlin
package com.agentwork.productspecagent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    val jwt: Jwt = Jwt(),
    val cookie: Cookie = Cookie(),
) {
    data class Jwt(
        val secret: String = "",
        val expirySeconds: Long = 604800, // 7 days
    )
    data class Cookie(
        val name: String = "session",
        val secure: Boolean = true,
    )
}
```

Sicherstellen, dass `AuthProperties::class` in `ProductSpecAgentApplication.kt` via `@ConfigurationPropertiesScan` (oder explizitem `@EnableConfigurationProperties(AuthProperties::class)`) registriert ist — Pattern aus existierendem `CorsProperties`/`S3StorageProperties` befolgen. Wenn `@ConfigurationPropertiesScan` bereits aktiv: nichts tun.

- [ ] **Step 3: `application.yml` erweitern**

Unter den existierenden Top-Level-Keys ergänzen:

```yaml
auth:
  jwt:
    secret: ${AUTH_JWT_SECRET:}
    expiry-seconds: 604800
  cookie:
    name: session
    secure: ${AUTH_COOKIE_SECURE:true}
```

`cors.allowed-origins` prüfen (Datei zeigt `http://localhost:3000`). Falls `frontend/package.json` Port 3001 nutzt, hier auf `http://localhost:3001` korrigieren — sonst 3000 lassen.

- [ ] **Step 4: Build ausführen**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/build.gradle.kts backend/src/main/resources/application.yml backend/src/main/kotlin/com/agentwork/productspecagent/config/AuthProperties.kt
git commit -m "chore(auth): add jjwt deps and auth config properties"
```

---

### Task 2: User-Domain + Auth-DTOs

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/User.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/AuthDtos.kt`

- [ ] **Step 1: `User.kt` schreiben**

```kotlin
package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,        // lowercase, eindeutig
    val passwordHash: String, // BCrypt, Strength 10
    val createdAt: String,    // ISO-8601 UTC
)
```

- [ ] **Step 2: `AuthDtos.kt` schreiben**

```kotlin
package com.agentwork.productspecagent.domain

data class AuthCredentials(
    val email: String,
    val password: String,
)

data class AuthMeResponse(
    val userId: String,
    val email: String,
)
```

- [ ] **Step 3: Compile prüfen**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/domain/User.kt backend/src/main/kotlin/com/agentwork/productspecagent/domain/AuthDtos.kt
git commit -m "feat(auth): add User domain and auth DTOs"
```

---

### Task 3: UserStorage (TDD, mit Email-Index)

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/storage/UserStorageTest.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/storage/UserStorage.kt`

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UserStorageTest : S3TestSupport() {

    private fun storage() = UserStorage(objectStore())

    private fun sample(id: String = "u1", email: String = "alice@example.com") = User(
        id = id, email = email, passwordHash = "{bcrypt}xxx",
        createdAt = "2026-05-05T10:00:00Z"
    )

    @Test
    fun `save and load user round-trip`() {
        val s = storage()
        s.saveUser(sample())
        val loaded = s.loadUser("u1")!!
        assertEquals("u1", loaded.id)
        assertEquals("alice@example.com", loaded.email)
    }

    @Test
    fun `loadUser returns null when not found`() {
        assertNull(storage().loadUser("missing"))
    }

    @Test
    fun `findByEmail returns user when registered`() {
        val s = storage()
        s.saveUser(sample(email = "Bob@Example.com"))  // mixed case
        val found = s.findByEmail("bob@example.com")!! // lookup lowercase
        assertEquals("u1", found.id)
    }

    @Test
    fun `findByEmail is case-insensitive on save`() {
        val s = storage()
        s.saveUser(sample(email = "MIXED@case.com"))
        assertNotNull(s.findByEmail("mixed@case.com"))
        assertNotNull(s.findByEmail("MIXED@case.com"))
    }

    @Test
    fun `findByEmail returns null when not registered`() {
        assertNull(storage().findByEmail("nobody@example.com"))
    }

    @Test
    fun `emailExists returns true after save, false otherwise`() {
        val s = storage()
        assertFalse(s.emailExists("alice@example.com"))
        s.saveUser(sample())
        assertTrue(s.emailExists("alice@example.com"))
        assertTrue(s.emailExists("ALICE@example.com"))
    }
}
```

- [ ] **Step 2: Test laufen lassen, soll fehlschlagen**

Run: `./gradlew test --tests "*.UserStorageTest" -i`
Expected: FAIL — `UserStorage` does not exist.

- [ ] **Step 3: `UserStorage.kt` implementieren**

```kotlin
package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.User
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Service

@Service
class UserStorage(private val objectStore: ObjectStore) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun userKey(userId: String) = "users/$userId.json"
    private fun emailKey(email: String) = "users/_index/email-${email.lowercase()}.json"

    @Serializable
    private data class EmailIndex(val userId: String)

    fun saveUser(user: User) {
        val normalized = user.copy(email = user.email.lowercase())
        objectStore.put(
            userKey(normalized.id),
            json.encodeToString(normalized).toByteArray(),
            "application/json"
        )
        objectStore.put(
            emailKey(normalized.email),
            json.encodeToString(EmailIndex(normalized.id)).toByteArray(),
            "application/json"
        )
    }

    fun loadUser(userId: String): User? {
        val bytes = objectStore.get(userKey(userId)) ?: return null
        return json.decodeFromString<User>(bytes.toString(Charsets.UTF_8))
    }

    fun findByEmail(email: String): User? {
        val bytes = objectStore.get(emailKey(email)) ?: return null
        val index = json.decodeFromString<EmailIndex>(bytes.toString(Charsets.UTF_8))
        return loadUser(index.userId)
    }

    fun emailExists(email: String): Boolean =
        objectStore.exists(emailKey(email))
}
```

- [ ] **Step 4: Test laufen lassen, soll grün sein**

Run: `./gradlew test --tests "*.UserStorageTest" -i`
Expected: PASS — alle 6 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/storage/UserStorage.kt backend/src/test/kotlin/com/agentwork/productspecagent/storage/UserStorageTest.kt
git commit -m "feat(auth): add UserStorage with email index"
```

---

### Task 4: JwtService (TDD)

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/auth/JwtServiceTest.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/auth/JwtService.kt`

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class JwtServiceTest {

    private val testSecret = "0123456789abcdef0123456789abcdef0123456789abcdef" // 48 bytes
    private val props = AuthProperties(jwt = AuthProperties.Jwt(secret = testSecret, expirySeconds = 60))

    private fun service(clock: Clock = Clock.systemUTC()) = JwtService(props, clock)

    @Test
    fun `sign and parse round-trip preserves userId and email`() {
        val s = service()
        val token = s.sign("user-123", "alice@example.com")
        val parsed = s.parse(token)!!
        assertEquals("user-123", parsed.userId)
        assertEquals("alice@example.com", parsed.email)
    }

    @Test
    fun `parse returns null for tampered token`() {
        val s = service()
        val token = s.sign("u", "e@x.com")
        val tampered = token.dropLast(2) + "XX"
        assertNull(s.parse(tampered))
    }

    @Test
    fun `parse returns null for token signed with different secret`() {
        val other = JwtService(
            AuthProperties(jwt = AuthProperties.Jwt(secret = "different-secret-different-secret-different", expirySeconds = 60)),
            Clock.systemUTC()
        )
        val foreign = other.sign("u", "e@x.com")
        assertNull(service().parse(foreign))
    }

    @Test
    fun `parse returns null for expired token`() {
        val past = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        val now = Clock.fixed(Instant.parse("2020-01-01T00:02:00Z"), ZoneOffset.UTC) // 2 min later, expiry 60s
        val token = JwtService(props, past).sign("u", "e@x.com")
        assertNull(JwtService(props, now).parse(token))
    }

    @Test
    fun `parse returns null for malformed token`() {
        assertNull(service().parse("not-a-jwt"))
        assertNull(service().parse(""))
    }

    @Test
    fun `JwtService throws on construction with empty secret`() {
        val empty = AuthProperties(jwt = AuthProperties.Jwt(secret = "", expirySeconds = 60))
        assertThrows(IllegalStateException::class.java) {
            JwtService(empty, Clock.systemUTC())
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, soll fehlschlagen**

Run: `./gradlew test --tests "*.JwtServiceTest" -i`
Expected: FAIL — `JwtService` does not exist.

- [ ] **Step 3: `JwtService.kt` implementieren**

```kotlin
package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date
import javax.crypto.SecretKey

data class JwtPayload(val userId: String, val email: String)

@Service
@Primary
class JwtService(
    private val props: AuthProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val key: SecretKey = run {
        check(props.jwt.secret.isNotBlank()) { "auth.jwt.secret must be set (e.g., AUTH_JWT_SECRET env var)" }
        Keys.hmacShaKeyFor(props.jwt.secret.toByteArray(Charsets.UTF_8))
    }

    fun sign(userId: String, email: String): String {
        val now = clock.instant()
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(props.jwt.expirySeconds)))
            .signWith(key)
            .compact()
    }

    fun parse(token: String): JwtPayload? = try {
        val claims = Jwts.parser()
            .verifyWith(key)
            .clock { Date.from(clock.instant()) }
            .build()
            .parseSignedClaims(token)
            .payload
        JwtPayload(
            userId = claims.subject,
            email = claims["email", String::class.java],
        )
    } catch (_: Exception) {
        null
    }
}
```

> Hinweis: `@Primary` ist hier reine Vorsichtsmaßnahme — Koog/Spring kann eigene `JwtService`-Beans definieren. Falls Konflikte auftauchen, ersetzen wir es später durch einen eigenen Bean-Namen. In der Praxis voraussichtlich nicht nötig.

- [ ] **Step 4: Test laufen lassen, soll grün sein**

Run: `./gradlew test --tests "*.JwtServiceTest" -i`
Expected: PASS — alle 6 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/auth/JwtService.kt backend/src/test/kotlin/com/agentwork/productspecagent/auth/JwtServiceTest.kt
git commit -m "feat(auth): add JwtService for HMAC-SHA256 token sign/parse"
```

---

### Task 5: AuthExceptions + UserService (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/AuthExceptions.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/service/UserService.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/service/UserServiceTest.kt`

- [ ] **Step 1: `AuthExceptions.kt` schreiben**

```kotlin
package com.agentwork.productspecagent.service

class InvalidCredentialsException : RuntimeException("Login fehlgeschlagen")
class EmailAlreadyExistsException : RuntimeException("Email bereits registriert")
class WeakPasswordException(message: String) : RuntimeException(message)
class InvalidEmailException(message: String) : RuntimeException(message)
```

- [ ] **Step 2: Failing Test schreiben**

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.storage.S3TestSupport
import com.agentwork.productspecagent.storage.UserStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UserServiceTest : S3TestSupport() {

    private val fixedClock = Clock.fixed(Instant.parse("2026-05-05T10:00:00Z"), ZoneOffset.UTC)
    private fun service() = UserService(UserStorage(objectStore()), BCryptPasswordEncoder(10), fixedClock)

    @Test
    fun `register creates user with hashed password`() {
        val u = service().register("alice@example.com", "password123")
        assertEquals("alice@example.com", u.email)
        assertTrue(u.passwordHash.startsWith("$2a$") || u.passwordHash.startsWith("$2b$"))
        assertNotEquals("password123", u.passwordHash)
    }

    @Test
    fun `register lowercases email`() {
        val u = service().register("Alice@EXAMPLE.com", "password123")
        assertEquals("alice@example.com", u.email)
    }

    @Test
    fun `register throws for weak password (less than 8 chars)`() {
        assertThrows(WeakPasswordException::class.java) {
            service().register("a@b.com", "short")
        }
    }

    @Test
    fun `register throws for password longer than 128 chars`() {
        assertThrows(WeakPasswordException::class.java) {
            service().register("a@b.com", "a".repeat(129))
        }
    }

    @Test
    fun `register throws for invalid email format`() {
        assertThrows(InvalidEmailException::class.java) { service().register("not-an-email", "password123") }
        assertThrows(InvalidEmailException::class.java) { service().register("@no-local.com", "password123") }
        assertThrows(InvalidEmailException::class.java) { service().register("no-at.com", "password123") }
    }

    @Test
    fun `register throws when email already registered (case-insensitive)`() {
        val s = service()
        s.register("alice@example.com", "password123")
        assertThrows(EmailAlreadyExistsException::class.java) {
            s.register("Alice@EXAMPLE.com", "password456")
        }
    }

    @Test
    fun `authenticate returns user for correct credentials`() {
        val s = service()
        val registered = s.register("alice@example.com", "password123")
        val authed = s.authenticate("alice@example.com", "password123")
        assertEquals(registered.id, authed.id)
    }

    @Test
    fun `authenticate is case-insensitive on email`() {
        val s = service()
        s.register("alice@example.com", "password123")
        assertNotNull(s.authenticate("Alice@EXAMPLE.com", "password123"))
    }

    @Test
    fun `authenticate throws for wrong password`() {
        val s = service()
        s.register("alice@example.com", "password123")
        assertThrows(InvalidCredentialsException::class.java) {
            s.authenticate("alice@example.com", "wrong")
        }
    }

    @Test
    fun `authenticate throws for unknown email`() {
        assertThrows(InvalidCredentialsException::class.java) {
            service().authenticate("nobody@example.com", "password123")
        }
    }
}
```

- [ ] **Step 3: Test laufen lassen, soll fehlschlagen**

Run: `./gradlew test --tests "*.UserServiceTest" -i`
Expected: FAIL — `UserService` does not exist.

- [ ] **Step 4: `UserService.kt` implementieren**

```kotlin
package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.User
import com.agentwork.productspecagent.storage.UserStorage
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

@Service
class UserService(
    private val storage: UserStorage,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun register(email: String, password: String): User {
        val normalizedEmail = email.trim().lowercase()
        validateEmail(normalizedEmail)
        validatePassword(password)
        if (storage.emailExists(normalizedEmail)) throw EmailAlreadyExistsException()

        val user = User(
            id = UUID.randomUUID().toString(),
            email = normalizedEmail,
            passwordHash = passwordEncoder.encode(password),
            createdAt = clock.instant().toString(),
        )
        storage.saveUser(user)
        return user
    }

    fun authenticate(email: String, password: String): User {
        val user = storage.findByEmail(email.trim().lowercase()) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(password, user.passwordHash)) throw InvalidCredentialsException()
        return user
    }

    private fun validateEmail(email: String) {
        if (!emailRegex.matches(email)) throw InvalidEmailException("Ungültiges Email-Format")
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) throw WeakPasswordException("Passwort muss mindestens 8 Zeichen haben")
        if (password.length > 128) throw WeakPasswordException("Passwort darf maximal 128 Zeichen haben")
    }
}
```

- [ ] **Step 5: `BCryptPasswordEncoder` als Bean registrieren**

In `config/SecurityConfig.kt` einen `@Bean` für `PasswordEncoder` ergänzen (genau einen — kommt mit Task 7 ohnehin hinzu, hier aber schon einbauen damit `UserService` injiziert werden kann):

```kotlin
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

// Innerhalb von SecurityConfig:
@Bean
fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(10)
```

- [ ] **Step 6: Test laufen lassen, soll grün sein**

Run: `./gradlew test --tests "*.UserServiceTest" -i`
Expected: PASS — alle 10 Tests grün.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/service/AuthExceptions.kt backend/src/main/kotlin/com/agentwork/productspecagent/service/UserService.kt backend/src/main/kotlin/com/agentwork/productspecagent/config/SecurityConfig.kt backend/src/test/kotlin/com/agentwork/productspecagent/service/UserServiceTest.kt
git commit -m "feat(auth): add UserService with email/password validation and BCrypt"
```

---

### Task 6: AuthCookieService

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/auth/AuthCookieService.kt`

- [ ] **Step 1: Implementierung schreiben**

```kotlin
package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service

@Service
class AuthCookieService(private val props: AuthProperties) {

    fun setSessionCookie(response: HttpServletResponse, token: String) {
        val cookie = ResponseCookie.from(props.cookie.name, token)
            .httpOnly(true)
            .secure(props.cookie.secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(props.jwt.expirySeconds)
            .build()
        response.addHeader("Set-Cookie", cookie.toString())
    }

    fun clearSessionCookie(response: HttpServletResponse) {
        val cookie = ResponseCookie.from(props.cookie.name, "")
            .httpOnly(true)
            .secure(props.cookie.secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build()
        response.addHeader("Set-Cookie", cookie.toString())
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/auth/AuthCookieService.kt
git commit -m "feat(auth): add AuthCookieService for httpOnly session cookies"
```

---

### Task 7: JwtAuthenticationFilter (TDD)

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/auth/JwtAuthenticationFilterTest.kt`
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/auth/JwtAuthenticationFilter.kt`

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTest {

    private val props = AuthProperties(jwt = AuthProperties.Jwt(secret = "0123456789abcdef0123456789abcdef0123456789abcdef", expirySeconds = 60))
    private val jwt = JwtService(props)
    private val filter = JwtAuthenticationFilter(jwt, props)

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `valid cookie sets SecurityContext authentication`() {
        val token = jwt.sign("u-1", "a@b.com")
        val req = MockHttpServletRequest().apply { setCookies(Cookie("session", token)) }
        val res = MockHttpServletResponse()
        val chain: FilterChain = mock()

        filter.doFilter(req, res, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals("u-1", auth.name)
        assertTrue(auth.isAuthenticated)
        verify(chain).doFilter(req, res)
    }

    @Test
    fun `missing cookie leaves SecurityContext empty`() {
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()
        val chain: FilterChain = mock()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(chain).doFilter(req, res)
    }

    @Test
    fun `invalid token cookie leaves SecurityContext empty without throwing`() {
        val req = MockHttpServletRequest().apply { setCookies(Cookie("session", "garbage")) }
        val res = MockHttpServletResponse()
        val chain: FilterChain = mock()

        assertDoesNotThrow { filter.doFilter(req, res, chain) }
        assertNull(SecurityContextHolder.getContext().authentication)
        verify(chain).doFilter(req, res)
    }

    @Test
    fun `wrong cookie name is ignored`() {
        val token = jwt.sign("u-1", "a@b.com")
        val req = MockHttpServletRequest().apply { setCookies(Cookie("other", token)) }
        val res = MockHttpServletResponse()
        val chain: FilterChain = mock()

        filter.doFilter(req, res, chain)
        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
```

> Hinweis: Falls `mockito-kotlin` noch nicht verfügbar ist (`testImplementation`), in Task 1 ergänzen oder die Mocks per Java-Interop `mock(FilterChain::class.java)` machen.

Falls die Lib fehlt, vor diesem Task in `build.gradle.kts` ergänzen:
```kotlin
testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
```

- [ ] **Step 2: Test laufen lassen, soll fehlschlagen**

Run: `./gradlew test --tests "*.JwtAuthenticationFilterTest" -i`
Expected: FAIL — `JwtAuthenticationFilter` does not exist.

- [ ] **Step 3: `JwtAuthenticationFilter.kt` implementieren**

```kotlin
package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val props: AuthProperties,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = request.cookies?.firstOrNull { it.name == props.cookie.name }?.value
        if (token != null) {
            val payload = jwtService.parse(token)
            if (payload != null) {
                val auth = UsernamePasswordAuthenticationToken(
                    payload.userId,
                    null,
                    emptyList()
                )
                SecurityContextHolder.getContext().authentication = auth
                request.setAttribute("authEmail", payload.email)
            }
        }
        filterChain.doFilter(request, response)
    }
}
```

- [ ] **Step 4: Test laufen lassen, soll grün sein**

Run: `./gradlew test --tests "*.JwtAuthenticationFilterTest" -i`
Expected: PASS — alle 4 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/auth/JwtAuthenticationFilter.kt backend/src/test/kotlin/com/agentwork/productspecagent/auth/JwtAuthenticationFilterTest.kt backend/build.gradle.kts
git commit -m "feat(auth): add JwtAuthenticationFilter reading session cookie"
```

---

### Task 8: SecurityConfig — Filter einbinden + Endpoints öffnen

**Files:**
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/config/SecurityConfig.kt`

- [ ] **Step 1: SecurityConfig komplett überschreiben**

```kotlin
package com.agentwork.productspecagent.config

import com.agentwork.productspecagent.auth.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(private val jwtAuthenticationFilter: JwtAuthenticationFilter) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(10)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors {}
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
        return http.build()
    }
}
```

- [ ] **Step 2: Compile prüfen**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Bestehende Tests ausführen — keine Regressions**

Run: `./gradlew test`
Expected: alle bestehenden Tests grün. Wenn Controller-Tests jetzt 401 statt 200 liefern, ist das ein Hinweis auf Tests, die `@WithMockUser` oder ähnliches brauchen — kurzes Audit der Test-Outputs. Existierende `@SpringBootTest`-Tests, die `/api/v1/projects/...` aufrufen, müssen ggf. mit `@WithMockUser` annotiert werden. Falls auftritt: in den betroffenen Test-Klassen `@WithMockUser` in den Klassen-Header schreiben (Import: `org.springframework.security.test.context.support.WithMockUser`).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/config/SecurityConfig.kt
git commit -m "feat(auth): wire JwtAuthenticationFilter into SecurityConfig"
```

> Falls in Step 3 bestehende Tests gefixt wurden, separater Folge-Commit:
> ```bash
> git add backend/src/test
> git commit -m "test: add @WithMockUser to controller tests under new auth gate"
> ```

---

### Task 9: AuthController + GlobalExceptionHandler-Erweiterung (TDD)

**Files:**
- Create: `backend/src/main/kotlin/com/agentwork/productspecagent/api/AuthController.kt`
- Modify: `backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt`
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/api/AuthControllerTest.kt`

- [ ] **Step 1: GlobalExceptionHandler ergänzen**

In `GlobalExceptionHandler.kt` neue Imports + vier Handler ergänzen:

```kotlin
import com.agentwork.productspecagent.service.EmailAlreadyExistsException
import com.agentwork.productspecagent.service.InvalidCredentialsException
import com.agentwork.productspecagent.service.InvalidEmailException
import com.agentwork.productspecagent.service.WeakPasswordException

// Innerhalb der Klasse:

@ExceptionHandler(InvalidCredentialsException::class)
@ResponseStatus(HttpStatus.UNAUTHORIZED)
fun handleInvalidCredentials(ex: InvalidCredentialsException): ErrorResponse =
    ErrorResponse("INVALID_CREDENTIALS", ex.message ?: "Login fehlgeschlagen", Instant.now().toString())

@ExceptionHandler(EmailAlreadyExistsException::class)
@ResponseStatus(HttpStatus.CONFLICT)
fun handleEmailExists(ex: EmailAlreadyExistsException): ErrorResponse =
    ErrorResponse("EMAIL_ALREADY_EXISTS", ex.message ?: "Email bereits registriert", Instant.now().toString())

@ExceptionHandler(WeakPasswordException::class, InvalidEmailException::class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
fun handleAuthValidation(ex: RuntimeException): ErrorResponse =
    ErrorResponse("VALIDATION_ERROR", ex.message ?: "Ungültige Eingabe", Instant.now().toString())
```

- [ ] **Step 2: Failing Test schreiben (MockMvc)**

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.config.AuthProperties
import com.agentwork.productspecagent.config.S3StorageProperties
import com.agentwork.productspecagent.storage.S3TestSupport
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie as cookieM
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerTest : S3TestSupport() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("auth.jwt.secret") { "0123456789abcdef0123456789abcdef0123456789abcdef" }
            registry.add("auth.cookie.secure") { "false" }
            registry.add("app.storage.bucket") { BUCKET }
            registry.add("app.storage.endpoint") { minio.s3URL }
            registry.add("app.storage.access-key") { minio.userName }
            registry.add("app.storage.secret-key") { minio.password }
            registry.add("app.storage.path-style-access") { "true" }
        }
    }

    private fun body(email: String, pw: String) =
        mapper.writeValueAsString(mapOf("email" to email, "password" to pw))

    @Test
    fun `register returns 201 with cookie and user info`() {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("alice@example.com", "password123")))
            .andExpect(status().isCreated)
            .andExpect(cookieM().exists("session"))
            .andExpect(cookieM().httpOnly("session", true))
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.userId").exists())
    }

    @Test
    fun `register duplicate email returns 409`() {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("dup@example.com", "password123")))
            .andExpect(status().isCreated)
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("dup@example.com", "password123")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"))
    }

    @Test
    fun `register weak password returns 400`() {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("a@b.com", "short")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
    }

    @Test
    fun `login with valid credentials returns 200 with cookie`() {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("login@example.com", "password123")))
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("login@example.com", "password123")))
            .andExpect(status().isOk)
            .andExpect(cookieM().exists("session"))
    }

    @Test
    fun `login with wrong password returns 401 with generic error`() {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("wp@example.com", "password123")))
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("wp@example.com", "wrong-password")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login with unknown email returns 401`() {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("missing@example.com", "password123")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me without cookie returns 401`() {
        mvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me with valid cookie returns 200 with user info`() {
        val res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("me@example.com", "password123")))
            .andReturn().response
        val cookie = res.getCookie("session")!!
        mvc.perform(get("/api/v1/auth/me").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("me@example.com"))
    }

    @Test
    fun `logout clears cookie and returns 204`() {
        val res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("lo@example.com", "password123")))
            .andReturn().response
        val cookie = res.getCookie("session")!!
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
            .andExpect(status().isNoContent)
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")))
    }
}
```

> Hinweis: Falls `app.storage.*`-Properties anders heißen, in `application.yml` nachprüfen und `DynamicPropertySource` entsprechend anpassen.

- [ ] **Step 3: Test laufen lassen, soll fehlschlagen**

Run: `./gradlew test --tests "*.AuthControllerTest" -i`
Expected: FAIL — `AuthController` does not exist.

- [ ] **Step 4: `AuthController.kt` implementieren**

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.auth.AuthCookieService
import com.agentwork.productspecagent.auth.JwtService
import com.agentwork.productspecagent.config.AuthProperties
import com.agentwork.productspecagent.domain.AuthCredentials
import com.agentwork.productspecagent.domain.AuthMeResponse
import com.agentwork.productspecagent.service.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val users: UserService,
    private val jwt: JwtService,
    private val cookies: AuthCookieService,
) {

    @PostMapping("/register")
    fun register(@RequestBody credentials: AuthCredentials, response: HttpServletResponse): ResponseEntity<AuthMeResponse> {
        val user = users.register(credentials.email, credentials.password)
        cookies.setSessionCookie(response, jwt.sign(user.id, user.email))
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthMeResponse(user.id, user.email))
    }

    @PostMapping("/login")
    fun login(@RequestBody credentials: AuthCredentials, response: HttpServletResponse): AuthMeResponse {
        val user = users.authenticate(credentials.email, credentials.password)
        cookies.setSessionCookie(response, jwt.sign(user.id, user.email))
        return AuthMeResponse(user.id, user.email)
    }

    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): ResponseEntity<Void> {
        cookies.clearSessionCookie(response)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): AuthMeResponse {
        val userId = SecurityContextHolder.getContext().authentication?.name
            ?: error("authentication missing — should have been blocked by SecurityFilterChain")
        val email = request.getAttribute("authEmail") as? String ?: ""
        return AuthMeResponse(userId, email)
    }
}
```

- [ ] **Step 5: Test laufen lassen, soll grün sein**

Run: `./gradlew test --tests "*.AuthControllerTest" -i`
Expected: PASS — alle 9 Tests grün.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/agentwork/productspecagent/api/AuthController.kt backend/src/main/kotlin/com/agentwork/productspecagent/api/GlobalExceptionHandler.kt backend/src/test/kotlin/com/agentwork/productspecagent/api/AuthControllerTest.kt
git commit -m "feat(auth): add AuthController with register/login/logout/me endpoints"
```

---

### Task 10: SecurityIntegrationTest — End-to-End Auth-Gate

**Files:**
- Create: `backend/src/test/kotlin/com/agentwork/productspecagent/api/SecurityIntegrationTest.kt`

- [ ] **Step 1: Test schreiben**

```kotlin
package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.storage.S3TestSupport
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest : S3TestSupport() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("auth.jwt.secret") { "0123456789abcdef0123456789abcdef0123456789abcdef" }
            registry.add("auth.cookie.secure") { "false" }
            registry.add("app.storage.bucket") { BUCKET }
            registry.add("app.storage.endpoint") { minio.s3URL }
            registry.add("app.storage.access-key") { minio.userName }
            registry.add("app.storage.secret-key") { minio.password }
            registry.add("app.storage.path-style-access") { "true" }
        }
    }

    @Test
    fun `health endpoint accessible without auth`() {
        mvc.perform(get("/api/health")).andExpect(status().isOk)
    }

    @Test
    fun `projects endpoint without cookie returns 401`() {
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `projects endpoint with valid session cookie does not return 401`() {
        val res = mvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("email" to "user@example.com", "password" to "password123"))))
            .andReturn().response
        val cookie = res.getCookie("session")!!

        // Wir erwarten *nicht* 401. 200 oder 404 sind beide OK — Hauptsache der Auth-Gate
        // hat den Request durchgelassen.
        val result = mvc.perform(get("/api/v1/projects").cookie(cookie)).andReturn()
        assert(result.response.status != 401) {
            "Expected request through auth gate, got 401"
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, soll grün sein**

Run: `./gradlew test --tests "*.SecurityIntegrationTest" -i`
Expected: PASS — alle 3 Tests grün.

- [ ] **Step 3: Volle Test-Suite**

Run: `./gradlew test`
Expected: alle Tests grün. Falls bestehende Tests rot werden (Auth-Gate blockt), `@WithMockUser` ergänzen wie in Task 8 beschrieben.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/kotlin/com/agentwork/productspecagent/api/SecurityIntegrationTest.kt
git commit -m "test(auth): add end-to-end security gate integration test"
```

---

### Task 11: dev-Profile + docker-compose

**Files:**
- Create: `backend/src/main/resources/application-dev.yml` (falls noch nicht existiert)
- Modify: `docker-compose.yml`

- [ ] **Step 1: Prüfen, ob `application-dev.yml` existiert**

Run: `ls backend/src/main/resources/`
Falls `application-dev.yml` nicht existiert → in Step 2 neu anlegen.

- [ ] **Step 2: `application-dev.yml` schreiben (oder ergänzen)**

```yaml
auth:
  jwt:
    secret: dev-secret-change-me-dev-secret-change-me-dev-secret
  cookie:
    secure: false
```

- [ ] **Step 3: `docker-compose.yml` ergänzen**

Im `backend`-Service unter `environment` ergänzen:

```yaml
      AUTH_JWT_SECRET: ${AUTH_JWT_SECRET:?AUTH_JWT_SECRET must be set in .env}
      AUTH_COOKIE_SECURE: ${AUTH_COOKIE_SECURE:-true}
```

`.env.example` (falls existiert) ergänzen:
```
AUTH_JWT_SECRET=replace-me-with-openssl-rand-base64-48
AUTH_COOKIE_SECURE=false
```

- [ ] **Step 4: Lokal hochfahren und prüfen**

Run: `./gradlew bootRun --quiet --args='--spring.profiles.active=dev'` (Hintergrund) oder von der IDE
Run (in neuem Terminal): `curl -i http://localhost:8080/api/health`
Expected: `200 OK` (kein Auth nötig).
Run: `curl -i http://localhost:8080/api/v1/projects`
Expected: `401 Unauthorized`.
Run: `curl -i -X POST http://localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{"email":"smoke@test.com","password":"password123"}'`
Expected: `201 Created` mit `Set-Cookie: session=...`.
Backend stoppen.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/application-dev.yml docker-compose.yml
# .env.example nur, falls die Datei vorher schon existierte
git commit -m "chore(auth): add dev profile and docker-compose env vars"
```

---

### Task 12: Frontend — `lib/api.ts` erweitern

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: `apiFetch` mit `credentials: 'include'` und 401-Handler erweitern**

Die `apiFetch`-Funktion in `lib/api.ts` ersetzen (Datei-Anfang) durch:

```ts
const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: unknown,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function apiFetch<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options?.headers,
    },
    ...options,
  });

  if (res.status === 401) {
    onUnauthorized?.();
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const bodyMessage =
      body && typeof body === "object" && "message" in body && typeof (body as { message?: unknown }).message === "string"
        ? (body as { message: string }).message
        : null;
    throw new ApiError(res.status, body, bodyMessage || `API error: ${res.status}`);
  }

  if (res.status === 204 || res.headers.get("content-length") === "0") {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}
```

- [ ] **Step 2: Auth-Types und API-Helpers ergänzen**

Am Ende von `lib/api.ts` (vor anderen Domain-Wrappern oder ans Ende) ergänzen:

```ts
// ─── Auth ────────────────────────────────────────────────────────────────────

export interface AuthMe {
  userId: string;
  email: string;
}

export async function authRegister(email: string, password: string): Promise<AuthMe> {
  return apiFetch<AuthMe>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function authLogin(email: string, password: string): Promise<AuthMe> {
  return apiFetch<AuthMe>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function authLogout(): Promise<void> {
  await apiFetch<void>("/api/v1/auth/logout", { method: "POST" });
}

export async function authMe(): Promise<AuthMe> {
  return apiFetch<AuthMe>("/api/v1/auth/me");
}
```

- [ ] **Step 3: Lint prüfen**

Run: `cd frontend && npm run lint`
Expected: keine neuen Fehler.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(auth): add credentials include, 401 handler, and auth api helpers"
```

---

### Task 13: Frontend — `auth-store`

**Files:**
- Create: `frontend/src/lib/stores/auth-store.ts`

- [ ] **Step 1: Store schreiben**

```ts
import { create } from "zustand";
import { authLogin, authLogout, authMe, authRegister, type AuthMe } from "@/lib/api";

type AuthStatus = "loading" | "authenticated" | "guest";

interface AuthState {
  user: AuthMe | null;
  status: AuthStatus;

  initialize: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  clear: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  status: "loading",

  initialize: async () => {
    try {
      const user = await authMe();
      set({ user, status: "authenticated" });
    } catch {
      set({ user: null, status: "guest" });
    }
  },

  login: async (email, password) => {
    const user = await authLogin(email, password);
    set({ user, status: "authenticated" });
  },

  register: async (email, password) => {
    const user = await authRegister(email, password);
    set({ user, status: "authenticated" });
  },

  logout: async () => {
    try {
      await authLogout();
    } finally {
      set({ user: null, status: "guest" });
    }
  },

  clear: () => set({ user: null, status: "guest" }),
}));
```

- [ ] **Step 2: Lint**

Run: `cd frontend && npm run lint`

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/stores/auth-store.ts
git commit -m "feat(auth): add zustand auth-store"
```

---

### Task 14: Frontend — AuthProvider + Root-Layout

**Files:**
- Create: `frontend/src/components/auth/AuthProvider.tsx`
- Modify: `frontend/src/app/layout.tsx`

- [ ] **Step 1: AuthProvider schreiben**

```tsx
"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/stores/auth-store";
import { setUnauthorizedHandler } from "@/lib/api";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const initialize = useAuthStore((s) => s.initialize);
  const clear = useAuthStore((s) => s.clear);
  const router = useRouter();

  useEffect(() => {
    initialize();
  }, [initialize]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clear();
      router.replace("/login");
    });
    return () => setUnauthorizedHandler(null);
  }, [clear, router]);

  return <>{children}</>;
}
```

- [ ] **Step 2: Root-Layout anpassen**

`frontend/src/app/layout.tsx` öffnen und den `<body>`-Inhalt mit `<AuthProvider>` umwickeln. Beispiel (exakte Struktur dem aktuellen Layout anpassen):

```tsx
import { AuthProvider } from "@/components/auth/AuthProvider";

// Innerhalb des Layouts:
<body>
  <AuthProvider>
    {/* bestehender Content / AppShell */}
    {children}
  </AuthProvider>
</body>
```

- [ ] **Step 3: Lint + Build**

Run: `cd frontend && npm run lint && npm run build`
Expected: erfolgreich.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/auth/AuthProvider.tsx frontend/src/app/layout.tsx
git commit -m "feat(auth): add AuthProvider and wire into root layout"
```

---

### Task 15: Frontend — `proxy.ts`

**Files:**
- Create: `frontend/src/proxy.ts`

- [ ] **Step 1: Proxy schreiben**

```ts
import { NextResponse, type NextRequest } from "next/server";

const PUBLIC_PATHS = ["/login", "/register"];

export default function proxy(req: NextRequest) {
  const path = req.nextUrl.pathname;
  const isPublic = PUBLIC_PATHS.some((p) => path === p || path.startsWith(`${p}/`));
  const hasSession = req.cookies.has("session");

  if (!isPublic && !hasSession) {
    const url = new URL("/login", req.nextUrl);
    url.searchParams.set("next", path + req.nextUrl.search);
    return NextResponse.redirect(url);
  }

  if (isPublic && hasSession) {
    return NextResponse.redirect(new URL("/", req.nextUrl));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
```

- [ ] **Step 2: Lint + Build**

Run: `cd frontend && npm run lint && npm run build`

- [ ] **Step 3: Commit**

```bash
git add frontend/src/proxy.ts
git commit -m "feat(auth): add Next.js proxy for cookie-based redirects"
```

---

### Task 16: Frontend — Login-Page + LoginForm

**Files:**
- Create: `frontend/src/app/(auth)/layout.tsx`
- Create: `frontend/src/app/(auth)/login/page.tsx`
- Create: `frontend/src/components/auth/LoginForm.tsx`

- [ ] **Step 1: Auth-Layout (leer, ohne AppShell)**

```tsx
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return <div className="min-h-screen flex items-center justify-center bg-background p-6">{children}</div>;
}
```

- [ ] **Step 2: Login-Page**

```tsx
import { Suspense } from "react";
import { LoginForm } from "@/components/auth/LoginForm";

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
```

- [ ] **Step 3: LoginForm**

```tsx
"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/lib/stores/auth-store";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const login = useAuthStore((s) => s.login);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      router.replace(params.get("next") ?? "/projects");
    } catch {
      setError("Login fehlgeschlagen");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="w-full max-w-sm space-y-4 rounded-lg border bg-card p-6">
      <h1 className="text-xl font-semibold">Anmelden</h1>

      <div className="space-y-2">
        <Label htmlFor="email">Email</Label>
        <Input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
      </div>

      <div className="space-y-2">
        <Label htmlFor="password">Passwort</Label>
        <Input id="password" type="password" required minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}

      <Button type="submit" disabled={submitting} className="w-full">
        {submitting ? "..." : "Anmelden"}
      </Button>

      <p className="text-sm text-muted-foreground">
        Noch kein Konto? <Link href="/register" className="underline">Registrieren →</Link>
      </p>
    </form>
  );
}
```

> Hinweis: Falls der bestehende `Button` aus `components/ui/button.tsx` keine `disabled`/`variant`-Props in dieser Form unterstützt, nutze stattdessen einen Standard `<button type="submit">` mit Tailwind. Per Code-Review Datei vorher kurz prüfen.

- [ ] **Step 4: Lint + Build**

Run: `cd frontend && npm run lint && npm run build`

- [ ] **Step 5: Commit**

```bash
git add "frontend/src/app/(auth)" frontend/src/components/auth/LoginForm.tsx
git commit -m "feat(auth): add login page and form"
```

---

### Task 17: Frontend — Register-Page + RegisterForm

**Files:**
- Create: `frontend/src/app/(auth)/register/page.tsx`
- Create: `frontend/src/components/auth/RegisterForm.tsx`

- [ ] **Step 1: Register-Page**

```tsx
import { RegisterForm } from "@/components/auth/RegisterForm";

export default function RegisterPage() {
  return <RegisterForm />;
}
```

- [ ] **Step 2: RegisterForm**

```tsx
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/lib/stores/auth-store";
import { ApiError } from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";

export function RegisterForm() {
  const router = useRouter();
  const register = useAuthStore((s) => s.register);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(email, password);
      router.replace("/projects");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError("Email bereits registriert");
      } else if (err instanceof ApiError && err.status === 400) {
        setError("Ungültige Eingabe (Email-Format oder Passwort < 8 Zeichen)");
      } else {
        setError("Registrierung fehlgeschlagen");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="w-full max-w-sm space-y-4 rounded-lg border bg-card p-6">
      <h1 className="text-xl font-semibold">Konto anlegen</h1>

      <div className="space-y-2">
        <Label htmlFor="email">Email</Label>
        <Input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
      </div>

      <div className="space-y-2">
        <Label htmlFor="password">Passwort (min. 8 Zeichen)</Label>
        <Input id="password" type="password" required minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
      </div>

      {error && <p className="text-sm text-red-500">{error}</p>}

      <Button type="submit" disabled={submitting} className="w-full">
        {submitting ? "..." : "Registrieren"}
      </Button>

      <p className="text-sm text-muted-foreground">
        Schon ein Konto? <Link href="/login" className="underline">Anmelden →</Link>
      </p>
    </form>
  );
}
```

- [ ] **Step 3: Lint + Build**

Run: `cd frontend && npm run lint && npm run build`

- [ ] **Step 4: Commit**

```bash
git add "frontend/src/app/(auth)/register" frontend/src/components/auth/RegisterForm.tsx
git commit -m "feat(auth): add register page and form"
```

---

### Task 18: Frontend — LogoutButton in AppShell

**Files:**
- Create: `frontend/src/components/auth/LogoutButton.tsx`
- Modify: `frontend/src/components/layout/AppShell.tsx`

- [ ] **Step 1: LogoutButton schreiben**

```tsx
"use client";

import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/stores/auth-store";

export function LogoutButton() {
  const router = useRouter();
  const logout = useAuthStore((s) => s.logout);
  const user = useAuthStore((s) => s.user);

  async function handle() {
    await logout();
    router.replace("/login");
  }

  return (
    <button
      onClick={handle}
      className="flex h-10 w-10 items-center justify-center rounded-lg text-sidebar-foreground hover:text-zinc-200 transition-colors duration-150"
      title={user ? `Logout (${user.email})` : "Logout"}
    >
      <LogOut size={20} />
    </button>
  );
}
```

- [ ] **Step 2: AppShell ergänzen**

In `frontend/src/components/layout/AppShell.tsx` im IconRail-`<div>` für Bottom-Buttons (Zeile 86-93) den `LogoutButton` ergänzen:

```tsx
import { LogoutButton } from "@/components/auth/LogoutButton";

// ... innerhalb von IconRail, im "flex flex-col items-center gap-1"-Block am Ende:
<div className="flex flex-col items-center gap-1">
  <LogoutButton />
  <button
    className="flex h-10 w-10 items-center justify-center rounded-lg text-sidebar-foreground hover:text-zinc-200 transition-colors duration-150"
    title="Settings"
  >
    <Settings size={20} />
  </button>
</div>
```

- [ ] **Step 3: Lint + Build**

Run: `cd frontend && npm run lint && npm run build`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/auth/LogoutButton.tsx frontend/src/components/layout/AppShell.tsx
git commit -m "feat(auth): add logout button to app shell sidebar"
```

---

### Task 19: Manual Smoke Test

**Files:** keine.

Backend + Frontend hochfahren, im Browser sieben Szenarien durchspielen.

- [ ] **Step 1: Beide Server starten**

In Terminal 1: `cd backend && ./gradlew bootRun --quiet --args='--spring.profiles.active=dev'`
In Terminal 2: `cd frontend && npm run dev`
Warten, bis beide bereit sind.

- [ ] **Step 2: Smoke 1 — Anonymer Aufruf**

Browser → `http://localhost:3000/projects` (oder 3001 — entsprechend des Frontend-Ports)
Expected: redirect zu `/login?next=/projects`. URL enthält `next`-Param.

- [ ] **Step 3: Smoke 2 — Registrierung**

Auf `/login`-Seite → „Registrieren" klicken → Formular ausfüllen mit `test@example.com` + `password123` → submit.
Expected: redirect zu `/projects`, eingeloggt. Browser-DevTools → Application → Cookies → `session` ist gesetzt, `HttpOnly=✓`.

- [ ] **Step 4: Smoke 3 — Reload**

Reload der Seite (`Cmd+R`).
Expected: bleibt auf `/projects`, kein kurzer Sprung auf `/login`. (Wenn Flash sichtbar: AuthProvider lädt zu langsam — akzeptabel, dokumentieren.)

- [ ] **Step 5: Smoke 4 — Logout**

Klick auf Logout-Button in der IconRail.
Expected: redirect zu `/login`. Cookie ist weg (DevTools prüfen).

- [ ] **Step 6: Smoke 5 — Login mit existierendem Account**

Auf `/login` mit `test@example.com` + `password123`.
Expected: redirect zu `/projects`, eingeloggt.

- [ ] **Step 7: Smoke 6 — Falsches Passwort**

Auf `/login` mit `test@example.com` + `wrong`.
Expected: Bleibt auf `/login`, Fehler-Toast/Text „Login fehlgeschlagen". Kein Cookie gesetzt.

- [ ] **Step 8: Smoke 7 — Cookie manuell löschen**

Eingeloggt, dann via DevTools `session`-Cookie löschen → `Cmd+R`.
Expected: redirect zu `/login`.

- [ ] **Step 9: Smoke 8 — Doppelte Registrierung**

Auf `/register` mit `test@example.com` + `password123` (existiert schon).
Expected: Bleibt auf `/register`, Fehler „Email bereits registriert".

- [ ] **Step 10: Acceptance-Check**

Alle 8 Akzeptanzkriterien aus der Feature-Doc (`docs/features/42-login-gate.md`) durchgehen — gemäß Definition grün.

- [ ] **Step 11: Final Commit (falls Cleanup)**

```bash
git status
# Falls keine Diffs: nichts zu tun.
# Falls kleine Cleanups nötig:
git add -A
git commit -m "chore(auth): cleanup after smoke verification"
```

---

## Self-Review Notizen

- **Spec-Coverage:** Alle 8 Akzeptanzkriterien aus `42-login-gate.md` haben einen Task: 1→T15, 2→T9+T17, 3→T18, 4→T14, 5→T12+T14, 6→T8+T10, 7→T9, 8→T4 (fail-fast in `JwtService` constructor — kein dedizierter Test, aber durch `JwtServiceTest.JwtService throws on construction with empty secret` abgedeckt). Backend-Tests grün → Akzeptanz 9 abgedeckt durch Tasks 3,4,5,7,9,10.
- **Type-Konsistenz:** `AuthMe` (Frontend) ↔ `AuthMeResponse` (Backend) — Felder `userId` + `email` matchen. `User.email` immer lowercase. Cookie-Name überall `session`.
- **Open Risk:** Bestehende Controller-Tests könnten unter dem neuen Auth-Gate rot werden. Task 8 Step 3 + Task 10 Step 3 fangen das ab — ggf. ein Folge-Commit mit `@WithMockUser`.
- **Frontend-Port:** Plan erwähnt 3000 ODER 3001 — erste Smoke-Step-Schritt muss tatsächlichen Port verifizieren und CORS-Origin in `application.yml` matchen.
