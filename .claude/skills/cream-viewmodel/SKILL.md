---
name: cream-viewmodel
description: How to create a ViewModel in the CREAM project. Use this skill whenever the user asks to add a ViewModel, wire up a screen, or create any state holder that needs a dependency from AppContainer. The pattern is manual DI via a companion object factory — no Hilt, no Koin.
---

# CREAM ViewModel Pattern

## The pattern

1. ViewModel takes repositories as constructor parameters.
2. A `companion object` owns a `factory(appContainer: AppContainer)` function that pulls the right repository and returns a `ViewModelProvider.Factory`.
3. At the call site, `LocalAppContainer.current` provides the container — no threading of dependencies through composable params.

## Template

```kotlin
class MyViewModel(private val myRepository: MyRepository) : ViewModel() {

    // state, flows, etc.

    companion object {
        fun factory(appContainer: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MyViewModel(appContainer.myRepository) as T
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

1. Add the interface to `:data` (e.g., `data/auth/MyRepository.kt`)
2. Add the implementation to `:data` (e.g., `data/auth/MyRepositoryImpl.kt`)
3. If it needs a datasource, add the datasource interface to `:data` and the impl to `:remote-datasource` or `:local-datasource`
4. Add the repository as a property on `AppContainer` (`data/di/AppContainer.kt`)
5. Wire the datasource impl in `CreamApplication`

## Key rules

- Repositories go in the ViewModel constructor — never fetched ad hoc inside the VM.
- The companion `factory` always takes `AppContainer`, not individual repositories. This keeps call sites uniform and means the VM owns the knowledge of which dependency it needs.
- `LocalAppContainer` is provided once at the root in `MainActivity` — never re-provided lower in the tree.
- No Hilt, no Koin, no service locator pattern elsewhere.
