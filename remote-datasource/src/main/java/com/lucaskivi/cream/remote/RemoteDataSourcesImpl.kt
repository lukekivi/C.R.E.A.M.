package com.lucaskivi.cream.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.lucaskivi.cream.auth.AuthDataSource
import com.lucaskivi.cream.auth.AuthDataSourceImpl
import com.lucaskivi.cream.institution.InstitutionRemoteDataSource
import com.lucaskivi.cream.institution.InstitutionRemoteDataSourceImpl
import com.lucaskivi.cream.institution.InstitutionTransformer

/**
 * Builds each remote datasource once and shares the clients they depend on.
 */
internal class RemoteDataSourcesImpl : RemoteDataSources {

    override val auth: AuthDataSource =
        AuthDataSourceImpl(
            firebaseAuth = FirebaseAuth.getInstance(),
        )

    override val institution: InstitutionRemoteDataSource =
        InstitutionRemoteDataSourceImpl(
            functions = firebaseFunctions,
            transformer = InstitutionTransformer(),
        )
}

/**
 * The process-wide [FirebaseFunctions] instance, pointed at the local Functions emulator in debug
 * builds and at deployed Functions in release builds.
 *
 * Held at file level so the emulator is configured exactly once per process, however many
 * [RemoteDataSourcesImpl]s are built. Firebase rejects `useEmulator` after the instance is first
 * used.
 */
private val firebaseFunctions: FirebaseFunctions by lazy {
    FirebaseFunctions
        .getInstance()
        .apply {
            if (BuildConfig.USE_FUNCTIONS_EMULATOR) {
                useEmulator(
                    FUNCTIONS_EMULATOR_HOST,
                    FUNCTIONS_EMULATOR_PORT,
                )
            }
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
