package dev.victorc.sunup.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test

class ResultTest {

    @Test
    fun mapTransformsSuccess() {
        val result: Result<Int, DataError> = Result.Success(2)

        assertThat(result.map { it * 10 }).isEqualTo(Result.Success(20))
    }

    @Test
    fun mapKeepsError() {
        val result: Result<Int, DataError> = Result.Error(DataError.Local.DISK_FULL)

        assertThat(result.map { it * 10 }).isEqualTo(Result.Error(DataError.Local.DISK_FULL))
    }

    @Test
    fun onSuccessAndOnFailureRunOnlyTheMatchingBranch() {
        var successCalled = false
        var failure: DataError? = null
        val success: Result<Int, DataError> = Result.Success(1)
        val error: Result<Int, DataError> = Result.Error(DataError.Local.NOT_FOUND)

        success
            .onSuccess { successCalled = true }
            .onFailure { failure = it }

        assertThat(successCalled).isTrue()
        assertThat(failure).isNull()

        successCalled = false
        error
            .onSuccess { successCalled = true }
            .onFailure { failure = it }

        assertThat(successCalled).isFalse()
        assertThat(failure).isEqualTo(DataError.Local.NOT_FOUND)
    }

    @Test
    fun asEmptyResultDropsTheValue() {
        val result: Result<String, DataError> = Result.Success("saved")

        assertThat(result.asEmptyResult()).isEqualTo(Result.Success(Unit))
    }
}
