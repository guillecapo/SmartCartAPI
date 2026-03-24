// src/test/java/com/msd/smartcart/shared/ResultTest.java

package com.msd.smartcart.shared;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultTest {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Result<String, AppError> success() {
        return Result.success("value");
    }

    private Result<String, AppError> failure() {
        return Result.failure(AppError.internal("test.error", "ctx"));
    }

    // -------------------------------------------------------------------------
    // Factory + state checks
    // -------------------------------------------------------------------------

    @Test
    void should_beSuccess_when_createdWithSuccess() {
        assertThat(success().isSuccess()).isTrue();
        assertThat(success().isFailure()).isFalse();
    }

    @Test
    void should_beFailure_when_createdWithFailure() {
        assertThat(failure().isFailure()).isTrue();
        assertThat(failure().isSuccess()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Value accessors
    // -------------------------------------------------------------------------

    @Test
    void should_returnValue_when_resultIsSuccess() {
        assertThat(success().getValue()).isEqualTo("value");
    }

    @Test
    void should_throwException_when_getValueCalledOnFailure() {
        assertThatThrownBy(() -> failure().getValue())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_returnError_when_resultIsFailure() {
        assertThat(failure().getError().code()).isEqualTo("test.error");
    }

    @Test
    void should_throwException_when_getErrorCalledOnSuccess() {
        assertThatThrownBy(() -> success().getError())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_returnOptionalWithValue_when_resultIsSuccess() {
        assertThat(success().toOptional()).isPresent().contains("value");
    }

    @Test
    void should_returnEmptyOptional_when_resultIsFailure() {
        assertThat(failure().toOptional()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Functional operations
    // -------------------------------------------------------------------------

    @Test
    void should_transformValue_when_mapCalledOnSuccess() {
        Result<Integer, AppError> result = success().map(String::length);
        assertThat(result.getValue()).isEqualTo(5);
    }

    @Test
    void should_propagateFailure_when_mapCalledOnFailure() {
        Result<Integer, AppError> result = failure().map(String::length);
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void should_chainResults_when_flatMapCalledOnSuccess() {
        Result<Integer, AppError> result = success()
                .flatMap(v -> Result.success(v.length()));
        assertThat(result.getValue()).isEqualTo(5);
    }

    @Test
    void should_propagateFailure_when_flatMapCalledOnFailure() {
        Result<Integer, AppError> result = failure()
                .flatMap(v -> Result.success(v.length()));
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void should_applySuccessFunction_when_foldCalledOnSuccess() {
        String folded = success().fold(v -> "ok:" + v, e -> "error:" + e.code());
        assertThat(folded).isEqualTo("ok:value");
    }

    @Test
    void should_applyFailureFunction_when_foldCalledOnFailure() {
        String folded = failure().fold(v -> "ok:" + v, e -> "error:" + e.code());
        assertThat(folded).isEqualTo("error:test.error");
    }

    // -------------------------------------------------------------------------
    // ifSuccess / ifFailure
    // -------------------------------------------------------------------------

    @Test
    void should_executeConsumer_when_ifSuccessCalledOnSuccess() {
        AtomicBoolean executed = new AtomicBoolean(false);
        success().ifSuccess(v -> executed.set(true));
        assertThat(executed.get()).isTrue();
    }

    @Test
    void should_notExecuteConsumer_when_ifSuccessCalledOnFailure() {
        AtomicBoolean executed = new AtomicBoolean(false);
        failure().ifSuccess(v -> executed.set(true));
        assertThat(executed.get()).isFalse();
    }

    @Test
    void should_executeConsumer_when_ifFailureCalledOnFailure() {
        AtomicBoolean executed = new AtomicBoolean(false);
        failure().ifFailure(e -> executed.set(true));
        assertThat(executed.get()).isTrue();
    }

    @Test
    void should_notExecuteConsumer_when_ifFailureCalledOnSuccess() {
        AtomicBoolean executed = new AtomicBoolean(false);
        success().ifFailure(e -> executed.set(true));
        assertThat(executed.get()).isFalse();
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    void should_printSuccessWithValue_when_toStringCalledOnSuccess() {
        assertThat(success().toString()).isEqualTo("Result.success(value)");
    }

    @Test
    void should_printFailureWithError_when_toStringCalledOnFailure() {
        assertThat(failure().toString()).contains("Result.failure");
    }
}