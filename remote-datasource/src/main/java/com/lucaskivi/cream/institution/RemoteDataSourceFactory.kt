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
    FirebaseFunctions
        .getInstance()
        .apply {
            if (BuildConfig.USE_FUNCTIONS_EMULATOR) {
                useEmulator(FUNCTIONS_EMULATOR_HOST, FUNCTIONS_EMULATOR_PORT)
            }
        }

/**
 * Device-local loopback address. Reaches the development machine only while
 * `adb reverse tcp:5001 tcp:5001` forwards the port, which works on both physical devices and
 * the Android emulator.
 */
private const val FUNCTIONS_EMULATOR_HOST = "127.0.0.1"

/**
 * Port the Functions emulator listens on, matching `emulators.functions.port` in `firebase.json`.
 */
private const val FUNCTIONS_EMULATOR_PORT = 5001
