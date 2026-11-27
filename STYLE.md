# STYLE

Code conventions for the CREAM codebase. All contributors (human and AI) follow these.

## Documentation

Every Kotlin declaration gets a KDoc comment — class, interface, object, function (member, extension, top-level), property, and parameter. This includes trivial-looking helpers and one-liners.

Exceptions:
- Local declarations inside a function body.
- Test sources. Test classes, tests, and test helpers don't get KDoc.
- Companion objects. Their members are documented instead.
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

When a function, constructor, or class declaration has any parameters, put each on its own line with a trailing comma, even when there is only one. The closing paren (and return type, if any) goes on its own line.

```kotlin
fun signInWithEmail(
    email: String,
    password: String,
): NetworkResult<User>

fun observeTransactions(
    accountId: String,
): Flow<List<Transaction>>
```

This applies to interfaces, abstract declarations, overrides, concrete implementations, and data classes alike. Adding or removing a parameter then touches only its own line. Lambdas and function types are exempt.

### Call arguments

In main code, a call with two or more arguments puts each argument on its own line with a trailing comma. The closing paren goes on its own line. This includes composables, `Modifier` calls, effect keys, and build scripts. Calls with one argument stay on one line. Tests are exempt.

```kotlin
Icon(
    imageVector = Icons.Default.Add,
    contentDescription = "Link an institution",
)
authRepository
    .signInWithEmail(
        email,
        password,
    )
```

`copy(...)` goes further, in tests too: every argument is named and on its own line, even when there is only one. An argument that takes more than a name or property access (an elvis, a call, a string template) goes in a local `val` first.

```kotlin
val errorMessage = cause.message ?: SIGN_IN_FAILED_MESSAGE
mutableUiState.update {
    it.copy(
        isLoading = false,
        errorMessage = errorMessage,
    )
}
```

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
fun <T : ViewModel> create(
    modelClass: Class<T>,
): T =
    LoginViewModel(appContainer.authRepository) as T
```

This minimizes diff churn if the body later needs to grow into a block body — only the header line changes (`=` → `{`) and a closing `}` is added; the body line stays put.

## When branches

When a `when` branch's body spans more than one line, wrap it in curly braces. Single-line branches stay bare. A branch with several conditions puts each condition on its own line.

```kotlin
when (state) {
    Status.Idle,
    Status.Loading -> Unit
    is Status.Failed -> {
        logger.log(state.cause)
        retryCount++
    }
}
```

## Method chains

When chaining two or more method calls (`.foo().bar()`), put the receiver alone on the first line, then each call on its own line, indented one level deeper. A constructor call such as `Foo.Builder()` counts as the receiver.

```kotlin
authRepository
    .signInWithEmail(email, password)
    .onSuccess { handleSignedIn() }
    .onFailure(::handleFailure)
```

Single-call chains (`foo.bar()`) stay on one line. Builder-style chains follow the same rule:

```kotlin
GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(false)
    .setServerClientId(serverClientId)
    .build()
```

## Returns

A function has at most one `return`, at the end. Don't return early, including `?: return` and labeled returns such as `return@LaunchedEffect`; branch with `if`/`when` instead. Several exit points make a function harder to follow.

## Extension receivers

Inside an extension function, reference the receiver's members with an explicit `this` (`this.error`, `this.toResult()`), so it's clear which values come from the receiver. Class members don't take `this`.

## ViewModel formatting

Every ViewModel primary constructor uses the per-line / trailing-comma format above:

```kotlin
class FooViewModel(
    private val someRepository: SomeRepository,
) : ViewModel() {
```

The `UiState` type is defined **below** the ViewModel class, not above. The ViewModel is the primary declaration; UiState is a supporting type.

Change `mutableUiState` with `update { }`, not by assigning its `value`.

## Declarative UiState

A screen's ViewModel owns one `UiState`, the screen's master state. It describes what the screen shows: which content, dialog, and flags are present. Use a sealed interface when the screen has distinct states (such as `Loading`, `Empty`, and `Loaded`); otherwise, plain properties are enough.

The `UiState` doesn't define how a component looks. Where it holds a component's input, it uses that composable's `*Data` type (see "Composable data"), built by the screen's UI transformer (see "UI transformers").

```kotlin
data class FooUiState(
    val content: FooContent = FooContent.Loading,
    val isRefreshing: Boolean = false,
)

sealed interface FooContent {
    data object Loading : FooContent
    data object Empty : FooContent
    data class Loaded(
        val items: List<FooItemData>,
    ) : FooContent
}
```

## Composable data

A composable owns the type that describes its input. The ViewModel never defines a component's shape.

- A composable that takes structured data takes one `data: <Name>Data` parameter, named after the composable: `AccountItem` takes `AccountItemData`. Name composables as nouns (`AccountItem`, `ProfileSection`), not by layout (`AccountRow`).
- The `*Data` type is declared in the composable's file, below the composable. A composable with a `*Data` type gets its own file, `<Name>.kt`, in the screen package. Private helpers it alone uses (e.g. `InstitutionHeader`) go in that file too.
- Every composable that takes structured data gets a `*Data` type. No exceptions: two screens sharing a domain model must not share its shape. Primitives and lambdas stay plain parameters (`ErrorDialog(message, onDismiss)`).
- A composable may take its children's `*Data` to pass down (`InstitutionList(institutions: List<InstitutionItemData>)`).
- `*Data` fields hold display-ready values: formatted strings, flags, ids. A composable never formats, cases, or joins them.
- Never build UI from a domain model. A `*Data` type (or the `UiState`) may carry a domain model only as a payload handed back through a callback, documented as such; the composable never reads its fields.
- A list section that contributes several lazy entries (a header, one entry per child, a divider) is a `LazyListScope.<name>` extension taking `<Name>Data`, lowercase by Compose convention (`institutionItem` takes `InstitutionItemData`). It keeps each child its own lazy entry.

```kotlin
/**
 * ...
 */
@Composable
fun AccountItem(
    data: AccountItemData,
) {
    ...
}

/**
 * What an [AccountItem] shows.
 * ...
 */
data class AccountItemData(
    val accountId: String,
    val name: String,
    val details: String,
    val balance: String,
)
```

## Events

A one-time instruction from a ViewModel to its screen (open an external flow, show a toast, navigate) is an event, not state. Events go on a separate `events` flow backed by a `Channel`, never in the `UiState`. The screen handles them with `EventsEffect`.

```kotlin
private val eventChannel = Channel<FooEvent>(Channel.BUFFERED)
val events: Flow<FooEvent> = eventChannel.receiveAsFlow()

sealed interface FooEvent {
    data class OpenLink(
        val linkToken: String,
    ) : FooEvent
}
```

```kotlin
EventsEffect(viewModel.events) { event ->
    when (event) {
        is FooEvent.OpenLink -> {
            linkLauncher.launch(event.linkToken)
        }
    }
}
```

## Packages

- A feature package holds behavior: interfaces, implementations, factories, composables.
- Data types it exposes (data classes, enums, sealed types) go in its `model` subpackage.
- A screen's `UiState` stays in the screen package, next to its ViewModel.
- A composable's `*Data` type stays in the composable's file.
- A screen's UI transformer stays in the screen package.

## Companion objects

Companion objects are declared at the end of the class body, after all other members.

## Constants

These rules cover non-UI constants for now.

- Declare constants as `const val` named in `SCREAMING_SNAKE_CASE`.
- A class's constants go in its companion object, not at the top level of the file, so they sit at the end of the class.
- A file with no class keeps its constants at the bottom of the file.
- Keep a constant `private` unless code outside its class uses it.

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

A test class with mocked collaborators calls `confirmVerified(...)` on all of them once, in its top-level `@AfterEach` `tearDown`, so any call a test didn't explicitly verify fails that test. Tests don't call it themselves. This means each test verifies every call it causes, including calls to stubbed methods.

- "Collaborators" are the mocks injected into the class under test, plus mocks that model a call the class makes across a boundary (e.g. a callable reference). Mocks that only stand in for data values, and mocks of library internals (e.g. a Firebase `Task`), are left out.
- A collaborator mock that a test creates itself (e.g. a callable returned by a setup helper) is out of `tearDown`'s reach, so that test confirms it at the end of its Verify section.
- Verify all expected calls in a single `verify { }` block, listed in the order they happen. Use `coVerify` if any of them suspends. Only split into another block when the verification parameters differ, e.g. `coVerify(exactly = 0) { }` for calls that must not happen.

```kotlin
class InstitutionRemoteDataSourceImplTest {

    private val functions: FirebaseFunctions = mockk()
    private val transformer: InstitutionTransformer = mockk()

    @AfterEach
    fun tearDown() {
        confirmVerified(functions, transformer)
    }

    @Nested
    inner class CreateLinkToken {

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
            confirmVerified(callable)
        }
    }
}
```

## Module / layer boundaries

A datasource module's only public entry point is its container: an interface with a property per datasource, and a factory function that builds it. Consumers use the container's properties; they never import an impl class.

```kotlin
// in :remote-datasource
interface RemoteDataSources {
    val auth: AuthDataSource
    val institution: InstitutionRemoteDataSource
}

fun remoteDataSources(): RemoteDataSources =
    RemoteDataSourcesImpl()
```

Impls have an `internal constructor` and are built only in the module's container impl. `AppContainer` in `:data` builds each module's container once. `:app` depends only on `:data`. See [docs/architecture.md](docs/architecture.md).

## Transformers

### Datasource transformers

When a datasource impl converts raw payloads (callable responses, API bodies) into models, that conversion lives in a `<Feature>Transformer` class in the same package, e.g. `InstitutionTransformer`. Keeping it out of the impl makes the mapping testable and lets impl tests mock it.

- The transformer is an `internal` class injected through the impl's constructor; the module's container passes the real one.
- Impl tests mock the transformer. They verify what is sent to the backend, that the response is handed to the transformer, and that the transformer's result (or failure) is returned.
- Outside datasource impls and screens, a conversion that nothing needs to mock can be a `private` or `internal` extension function in the file that uses it.
- Helpers that aren't specific to one feature, such as casts, go in a neutral extensions file in the module (e.g. `com.lucaskivi.cream.remote.CastExtensions`), not in the transformer.

### UI transformers

Every conversion from a domain model to something a screen shows lives in the screen's `<Screen>UiTransformer`, e.g. `InstitutionsUiTransformer`. That covers composables' `*Data` and any `UiState` value derived from a domain model, such as a dialog naming an institution. The ViewModel orchestrates; it doesn't format. A screen that shows nothing derived from a domain model (e.g. Login) has no UI transformer.

- The transformer is a class in the screen package, injected through the ViewModel's constructor. The ViewModel's factory constructs it, passing its own dependencies (e.g. `CurrencyFormatter`).
- Methods are named `to<Target>`: `toAccountItemData(account)`, `toConfirmDisconnect(institution)`.
- `<Screen>UiTransformerTest` covers the mapping and formatting. ViewModel tests mock the transformer and verify that domain models are handed to it and its results land in the `UiState`; they don't assert on formatted strings.
- Composables never call the transformer.

```kotlin
class FooViewModel(
    private val fooRepository: FooRepository,
    private val uiTransformer: FooUiTransformer,
) : ViewModel()
```
