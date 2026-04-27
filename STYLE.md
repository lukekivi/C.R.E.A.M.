# STYLE

Code conventions for the CREAM codebase. All contributors (human and AI) follow these.

## Documentation

Every Kotlin declaration gets a KDoc comment — class, interface, object, function (member, extension, top-level), property, and parameter. This includes trivial-looking helpers and one-liners.

Exceptions:
- Local declarations inside a function body.
- `override` members — they inherit KDoc from the supertype declaration. Only add KDoc on an override if its behavior diverges from the parent contract in a way the caller needs to know.

KDoc is always **multiline**, even for one-sentence comments. Never single-line (`/** Signs out. */`):

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

## Function and constructor parameters

When a function, constructor, or class declaration has 2+ parameters, put each on its own line with a trailing comma. The closing paren (and return type, if any) goes on its own line.

```kotlin
fun signInWithEmail(
    email: String,
    password: String,
): NetworkResult<User>
```

This applies to interfaces, abstract declarations, overrides, and concrete implementations alike.

## Expression-body functions

When a function uses `=` to specify an expression body, place the body expression on a new line, indented under the function header. Do not write `=` and the body on the same line.

```kotlin
fun <T : ViewModel> create(modelClass: Class<T>): T =
    LoginViewModel(appContainer.authRepository) as T
```

This minimizes diff churn if the body later needs to grow into a block body — only the header line changes (`=` → `{`) and a closing `}` is added; the body line stays put.

## Method chains

When chaining method calls (`.foo().bar().baz()`), keep the receiver and its first call together, then break each subsequent chained call onto its own line, indented one level deeper.

```kotlin
authRepository.signInWithEmail(email, password)
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

## Module / layer boundaries

Each module owns its implementation behind a factory function that returns the interface type. Consumers call the factory; they never import the impl class directly.

```kotlin
// in :remote-datasource
fun authDataSource(): AuthDataSource = AuthDataSourceImpl.create()
```

`AppContainer` in `:data` wires impls by calling each module's factory. `:app` depends only on `:data`.
