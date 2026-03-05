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

### Dependency Management
This app does not use a dependency injection tool like Hilt or Koin. Instead, it manually manages dependencies and provides them to ViewModel's via a factory.
