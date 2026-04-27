# C.R.E.A.M.
A budget app, because cash rules everything around me.

## Architecture

### Layering
The app is divided into three layers: UI, Data, and Datasource. 

#### UI Layer
The role of the UI layer (or presentation layer) is to display the application data on screen. Whenever the data changes, either due to user interaction (such as pressing a button) or external input (such as a network response), the UI should update to reflect the changes.

The UI layer comprises two types of constructs:

UI elements that render the data on the screen. You build these elements using Jetpack Compose functions to support adaptive layouts.
State holders that hold data, expose it to the UI, and handle logic. This could be a remembered object, a ViewModel, or some other UI layer controller.

### Data Layer
The data layer of an app contains the business logic. Business logic is what gives value to your app—it comprises rules that determine how your app creates, stores, and changes data.

The data layer is made up of repositories each of which can contain zero to many data sources. You should create a repository class for each different type of data you handle in your app.

### Datasource Layer
This layer is responsible for network calls and accessing local data storage, and is divided into two modules, the `remote-datasource`, and the `local-datasource`.

Each datasource module owns its interface, implementation, and a factory function that returns the interface type. Consumers never import implementation classes directly — they call the factory. This keeps each layer ignorant of the layers below it.

### Module Structure
```
:app                 — UI (Compose screens, ViewModels), MainActivity
:data                — repositories, domain models, AppContainer (wires datasource impls)
:remote-datasource   — RemoteDataSource interface + implementation
:local-datasource    — LocalDataSource interface + implementation
:core                — primitives shared across modules (e.g. NetworkResult)
```

Dependency graph:
```
:app → :data
:data → :remote-datasource, :local-datasource, :core
:remote-datasource → :core
:local-datasource  → (no module deps)
```

### Dependency Management
This app does not use a dependency injection tool like Hilt or Koin. Instead, it manually manages dependencies and provides them to ViewModel's via a factory.

`AppContainer` (in `:data`) holds all repositories and is instantiated in `CreamApplication`. Composables access it by casting `LocalContext.current.applicationContext` to `AppContainerProvider` and passing the relevant repository to a ViewModel factory.
