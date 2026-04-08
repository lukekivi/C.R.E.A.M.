package com.lucaskivi.cream.auth

/**
 * Creates the production [AuthDataSource] implementation.
 */
fun authDataSource(): AuthDataSource = AuthDataSourceImpl.create()
