---
name: cream-viewmodel
description: How to create a ViewModel in the CREAM project. Use this skill whenever the user asks to add a ViewModel, wire up a screen, or create any state holder that needs a dependency from AppContainer. The pattern is manual DI via a companion object factory — no Hilt, no Koin.
---

# CREAM ViewModel Pattern

## The pattern

1. ViewModel takes repositories as constructor parameters, plus its screen's `<Screen>UiTransformer` when the screen shows anything derived from a domain model.
2. A `companion object` owns a `factory(appContainer: AppContainer)` function that pulls the right repository and returns a `ViewModelProvider.Factory`.
3. At the call site, `LocalAppContainer.current` provides the container — no threading of dependencies through composable params.

## Template

```kotlin
class MyViewModel(
    private val myRepository: MyRepository,
    private val uiTransformer: MyUiTransformer,
) : ViewModel() {

    // state, flows, etc.

    companion object {
        fun factory(
            appContainer: AppContainer,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T =
                    MyViewModel(
                        myRepository = appContainer.myRepository,
                        uiTransformer = MyUiTransformer(),
                    ) as T
            }
    }
}
```

## Call site (inside a composable)

```kotlin
val appContainer = LocalAppContainer.current
val myViewModel: MyViewModel = viewModel(
    factory = MyViewModel.factory(appContainer)
)
```

## Wiring a new repository into AppContainer

If the ViewModel needs a repository that doesn't exist yet:

1. If it needs a new datasource, add the interface and an impl with an `internal constructor` to `:remote-datasource` or `:local-datasource` (`<module>/.../<feature>/`). Expose it as a property on the module's container (`RemoteDataSources` / `LocalDataSources`), built in the container impl.
2. Add the repository interface and impl to `:data` (`data/.../repository/<feature>/`). The impl takes datasource interfaces in its constructor.
3. Add the repository as a property on `AppContainer` (`data/.../di/AppContainer.kt`), typed as the interface and built from the module containers' datasources.

See "Dependency injection" in [docs/architecture.md](../../../docs/architecture.md).

## Key rules

- Repositories go in the ViewModel constructor — never fetched ad hoc inside the VM.
- The companion `factory` always takes `AppContainer`, not individual repositories. This keeps call sites uniform and means the VM owns the knowledge of which dependency it needs.
- `LocalAppContainer` is provided once at the root in `MainActivity` — never re-provided lower in the tree.
- No Hilt, no Koin, no service locator pattern elsewhere.
- `UiState` describes what the screen shows. See "Declarative UiState" in STYLE.md.
- Composables own their input types (`AccountItem` takes `AccountItemData`, declared in `AccountItem.kt`). The VM never defines a component's shape and never formats. See "Composable data" in STYLE.md.
- Every domain → UI conversion goes through the screen's `<Screen>UiTransformer`, which the factory constructs. VM tests mock it; `<Screen>UiTransformerTest` tests it. See "UI transformers" in STYLE.md.
- One-time instructions to the screen go on an `events` flow, not in `UiState`. See "Events" in STYLE.md.
- Dependencies that don't come from `AppContainer` are constructed in the factory and passed through the constructor too.
