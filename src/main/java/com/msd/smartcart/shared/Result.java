package com.msd.smartcart.shared;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Result<T, E> {

    private final T value;
    private final E error;
    private final boolean success;

    private Result(T value, E error, boolean success) {
        this.value = value;
        this.error = error;
        this.success = success;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    public static <T, E> Result<T, E> success(T value) {
        return new Result<>(value, null, true);
    }

    public static <T, E> Result<T, E> failure(E error) {
        return new Result<>(null, error, false);
    }

    // -------------------------------------------------------------------------
    // State checks
    // -------------------------------------------------------------------------

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    // -------------------------------------------------------------------------
    // Value accessors
    // -------------------------------------------------------------------------

    public T getValue() {
        if (!success) {
            throw new IllegalStateException("Result is a failure — no value present");
        }
        return value;
    }

    public E getError() {
        if (success) {
            throw new IllegalStateException("Result is a success — no error present");
        }
        return error;
    }

    public Optional<T> toOptional() {
        return success ? Optional.ofNullable(value) : Optional.empty();
    }

    // -------------------------------------------------------------------------
    // Functional operations
    // -------------------------------------------------------------------------

    public <U> Result<U, E> map(Function<T, U> mapper) {
        if (success) {
            return Result.success(mapper.apply(value));
        }
        return Result.failure(error);
    }

    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        if (success) {
            return mapper.apply(value);
        }
        return Result.failure(error);
    }

    public <U> U fold(Function<T, U> onSuccess, Function<E, U> onFailure) {
        return success ? onSuccess.apply(value) : onFailure.apply(error);
    }

    public Result<T, E> ifSuccess(Consumer<T> consumer) {
        if (success) {
            consumer.accept(value);
        }
        return this;
    }

    public Result<T, E> ifFailure(Consumer<E> consumer) {
        if (!success) {
            consumer.accept(error);
        }
        return this;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return success
                ? "Result.success(" + value + ")"
                : "Result.failure(" + error + ")";
    }
}