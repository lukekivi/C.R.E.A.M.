# STYLE

Code conventions for the CREAM codebase. All contributors (human and AI) follow these.

## Documentation

Every Kotlin declaration gets a KDoc comment — class, interface, object, function (member, extension, top-level), property, and parameter. This includes trivial-looking helpers and one-liners.

Exceptions:
- Local declarations inside a function body.
- Test sources. Test classes, tests, and test helpers don't get KDoc.
- `override` members — they inherit KDoc from the supertype declaration. Only add KDoc on an override if its behavior diverges from the parent contract in a way the caller needs to know.

KDoc is always **multiline**, even for one-sentence comments. Never use the single-line `/** ... */` form anywhere; where a multiline KDoc isn't wanted, a `//` comment is fine.

```kotlin
/**
 * Signs the current user out.
 */
fun signOut()
```

### Parameters

Every parameter on a function, constructor, or class declaration is documented with an `@param` tag in the enclosing KDoc — including for primary-constructor parameters that double as properties. If a function has zero parameters, no `@param` tags are written.

```kotlin
/**
 * Signs in with [email] and [password], returning a [NetworkResult] wrapping the signed-in [User].
 *
 * @param email Email address of an existing account.
 * @param password Password for that account.
 */
suspend fun signInWithEmail(
    email: String,
    password: String,
): NetworkResult<User>
```

`@return` is optional — only add it when the return value carries meaning that isn't obvious from the function description and signature.

### Properties

Every property declared at the top level, in a class, interface, or object — `val`, `var`, computed, or backing — gets its own KDoc directly above it.

```kotlin
/**
 * The currently signed-in user, or null if no session is active.
 */
val currentUser: User?
```

Properties promoted from primary-constructor parameters (`class Foo(val bar: Bar)`) are documented via `@param`/`@property` on the class KDoc rather than a separate KDoc on the parameter.

### Backing properties

When a class exposes an immutable view of a mutable property, name the mutable holder `mutableX` and the public view `x`. Do not use the underscore-prefix idiom (`_x`).

```kotlin
private val mutableUiState = MutableStateFlow(LoginUiState())
val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()
```

### Public vs. internal docs

KDoc describes the **contract** — what a caller can rely on — not the implementation. Strongly avoid leaking implementation details into KDoc on public-facing declarations (anything visible outside its declaring file: `public`, `internal`, `protected`, and `private` declarations exposed via inline functions).

Bad — leaks the backing tech, which can change without breaking the contract:
```kotlin
/**
 * Signs in by calling FirebaseAuth.signInWithEmailAndPassword and awaiting the Task.
 */
suspend fun signInWithEmail(...)
```

Good — describes behavior the caller cares about:
```kotlin
/**
 * Signs in with [email] and [password]. Returns a failure if the credentials are rejected
 * or the network is unavailable.
 */
suspend fun signInWithEmail(...)
```

For `private` declarations, still prefer behavior-first phrasing. Only mention implementation details when a maintainer would otherwise be misled — e.g. a non-obvious invariant, a workaround for a specific bug, or a subtle ordering requirement. If you can delete the impl-detail sentence without making the code harder to maintain, delete it.

**Vendor names in KDoc.** Don't name a vendor or backing technology (Plaid, Firebase, Room, Retrofit…) in KDoc for types that are meant to be vendor-neutral — e.g. domain models in `:core`. For tightly-coupled classes the coupling is explicit and intentional, so naming the vendor is fine.

## Function and constructor parameters

When a function, constructor, or class declaration has 2+ parameters, put each on its own line with a trailing comma. The closing paren (and return type, if any) goes on its own line.

```kotlin
fun signInWithEmail(
    email: String,
    password: String,
): NetworkResult<User>
```

This applies to interfaces, abstract declarations, overrides, and concrete implementations alike.

### Multi-line arguments

When an argument expression at a call site spans more than one line, extract it into a local `val` first and pass the `val`.

```kotlin
val request = mapOf(
    "itemId" to itemId,
    "cursor" to cursor,
)
val response = functions
    .getHttpsCallable(SYNC_TRANSACTIONS)
    .call(request)
```

## Expression-body functions

When a function uses `=` to specify an expression body, place the body expression on a new line, indented under the function header. Do not write `=` and the body on the same line.

```kotlin
fun <T : ViewModel> create(modelClass: Class<T>): T =
    LoginViewModel(appContainer.authRepository) as T
```

This minimizes diff churn if the body later needs to grow into a block body — only the header line changes (`=` → `{`) and a closing `}` is added; the body line stays put.

## Method chains

When chaining two or more method calls (`.foo().bar()`), put the receiver alone on the first line, then each call on its own line, indented one level deeper. A constructor call such as `Foo.Builder()` counts as the receiver.

```kotlin
authRepository
    .signInWithEmail(email, password)
    .onSuccess { mutableUiState.update { it.copy(isLoading = false) } }
    .onFailure { cause -> mutableUiState.update { it.copy(isLoading = false, error = cause.message) } }
```

Single-call chains (`foo.bar()`) stay on one line. Builder-style chains follow the same rule:

```kotlin
GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(false)
    .setServerClientId(serverClientId)
    .build()
```

## ViewModel formatting

Every ViewModel primary constructor uses the per-line / trailing-comma format above, even for a single parameter:

```kotlin
class FooViewModel(
    private val someRepository: SomeRepository,
) : ViewModel() {
```

The `UiState` data class is defined **below** the ViewModel class, not above. The ViewModel is the primary declaration; UiState is a supporting type.

## Packages

Model types (data classes and enums that carry data between layers) live in a `model` subpackage of their feature package, e.g. `com.lucaskivi.cream.core.institution.model`. The feature package itself holds the behavior: interfaces, implementations, factories.

## Companion objects

Companion objects are declared at the end of the class body, after all other members.

## Tests

Group a test class's tests by the method under test, each group in a JUnit 5 `@Nested inner class` named after that method. Test names then describe only the behavior. Don't use `// region` comments.

```kotlin
class InstitutionTransformerTest {

    @Nested
    inner class ToLinkToken {

        @Test
        fun `returns the link token`() {
            ...
        }
    }
}
```

### Setup / Test / Verify

Split every test body into `// Setup`, `// Test`, and `// Verify` sections, separated by a blank line, so a reviewer can see at a glance what is arranged, what is exercised, and what is checked. Leave out any section that would be empty.

- **Setup**: fixtures, mocks, stubs, and expected values. Any expected value that involves construction (e.g. `NetworkResult.Success("lt-abc")`, `listOf(domain)`, `LocalDate.of(...)`) goes in an `expected` val (or `expected<Thing>` when there are several) rather than inline in an assertion.
- **Test**: the call(s) under test. Capture results in a `val` instead of calling inside an assertion.
- **Verify**: assertions and `verify`/`coVerify` calls.

A constructed value used in more than one place in a test (e.g. passed to the call under test and checked in a `verify`) goes in a `val` in Setup, or is read back from the fixture that already holds it (e.g. `delta.removedIds`).

When the assertion itself runs the call (e.g. `assertThrows`), there is no separate Test section; the test only gets a `// Verify` section.

### Confirm verified

Every test that uses mocked collaborators ends its Verify section with `confirmVerified(...)` on all of them, so any call the test didn't explicitly verify fails the test. This means each test verifies every call it causes, including calls to stubbed methods.

- "Collaborators" are the mocks injected into the class under test, plus mocks that model a call the class makes across a boundary (e.g. a callable reference). Mocks that only stand in for data values, and mocks of library internals (e.g. a Firebase `Task`), are left out.
- Verify all expected calls in a single `verify { }` block, listed in the order they happen. Use `coVerify` if any of them suspends. Only split into another block when the verification parameters differ, e.g. `coVerify(exactly = 0) { }` for calls that must not happen.

```kotlin
@Test
fun `returns the transformed link token`() = runTest {
    // Setup
    val callable = setupCallable(CREATE_LINK_TOKEN, response)
    every { transformer.toLinkToken(response) } returns "lt-abc"
    val expected = NetworkResult.Success("lt-abc")

    // Test
    val result = dataSource.createLinkToken()

    // Verify
    assertEquals(expected, result)
    verify {
        functions.getHttpsCallable(CREATE_LINK_TOKEN)
        callable.call()
        transformer.toLinkToken(response)
    }
    confirmVerified(functions, callable, transformer)
}
```

## Module / layer boundaries

Each module owns its implementation behind a factory function that returns the interface type. Consumers call the factory; they never import the impl class directly.

```kotlin
// in :remote-datasource
fun authDataSource(): AuthDataSource = AuthDataSourceImpl.create()
```

`AppContainer` in `:data` wires impls by calling each module's factory. `:app` depends only on `:data`.

## Transformers

When a datasource impl converts raw payloads (callable responses, API bodies) into models, that conversion lives in a `<Feature>Transformer` class in the same package, e.g. `InstitutionTransformer`. Keeping it out of the impl makes the mapping testable and lets impl tests mock it.

- The transformer is an `internal` class injected through the impl's constructor; the module's factory passes the real one.
- Impl tests mock the transformer. They verify what is sent to the backend, that the response is handed to the transformer, and that the transformer's result (or failure) is returned.
- Helpers that aren't specific to one feature, such as casts, go in a neutral extensions file in the module (e.g. `com.lucaskivi.cream.remote.CastExtensions`), not in the transformer.
