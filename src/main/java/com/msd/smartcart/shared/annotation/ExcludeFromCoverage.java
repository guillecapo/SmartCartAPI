package com.msd.smartcart.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as excluded from JaCoCo coverage reports.
 * Use ONLY on records or classes with no business logic of their own.
 * If you add a method with logic, remove this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcludeFromCoverage {}