package com.lucaskivi.cream.auth

import com.google.firebase.auth.FirebaseAuth

/**
 * Creates the production [AuthDataSource] implementation.
 */
fun authDataSource(): AuthDataSource =
    AuthDataSourceImpl(
        firebaseAuth = FirebaseAuth.getInstance(),
    )
