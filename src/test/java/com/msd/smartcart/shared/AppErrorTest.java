package com.msd.smartcart.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppErrorTest {

    @Test
    void should_createPersistenceError_when_persistenceFactoryCalled() {
        AppError error = AppError.persistence("db.failed", "some-context");

        assertThat(error.code()).isEqualTo("db.failed");
        assertThat(error.context()).isEqualTo("some-context");
        assertThat(error.message()).isNotBlank();
    }

    @Test
    void should_createUnauthorizedError_when_unauthorizedFactoryCalled() {
        AppError error = AppError.unauthorized("some-context");

        assertThat(error.code()).isEqualTo("unauthorized");
        assertThat(error.context()).isEqualTo("some-context");
    }

    @Test
    void should_createNotFoundError_when_notFoundFactoryCalled() {
        AppError error = AppError.notFound("resource.not_found", "some-context");

        assertThat(error.code()).isEqualTo("resource.not_found");
        assertThat(error.context()).isEqualTo("some-context");
    }

    @Test
    void should_createInternalError_when_internalFactoryCalled() {
        AppError error = AppError.internal("internal.error", "some-context");

        assertThat(error.code()).isEqualTo("internal.error");
        assertThat(error.context()).isEqualTo("some-context");
    }
}