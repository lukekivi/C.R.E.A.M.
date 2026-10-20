package com.lucaskivi.cream.institution

import com.google.firebase.functions.FirebaseFunctions
import com.lucaskivi.cream.remote.BuildConfig

/**
 * Creates the [InstitutionRemoteDataSource] implementation for the current build type.
 */
fun institutionRemoteDataSource(): InstitutionRemoteDataSource =
    InstitutionRemoteDataSourceImpl(
        functions = functions(),
        transformer = InstitutionTransformer(),
    )

/**
 * Returns the [FirebaseFunctions] instance, pointed at the local Functions emulator in debug
 * builds and at deployed Functions in release builds.
 *
 * Calls made on the shared instance before `useEmulator` would go to deployed Functions; this
 * can't happen because the factory runs at app startup, before any callable is used.
 */
private fun functions(): FirebaseFunctions =
    FirebaseFunctions.getInstance()
        .apply {
            if (BuildConfig.USE_FUNCTIONS_EMULATOR) {
                useEmulator(FUNCTIONS_EMULATOR_HOST, FUNCTIONS_EMULATOR_PORT)
            }
        }

/**
 * Host the Android emulator uses to reach the development machine's loopback interface.
 */
private const val FUNCTIONS_EMULATOR_HOST = "10.0.2.2"

/**
 * Port the Functions emulator listens on, matching `emulators.functions.port` in `firebase.json`.
 */
private const val FUNCTIONS_EMULATOR_PORT = 5001
