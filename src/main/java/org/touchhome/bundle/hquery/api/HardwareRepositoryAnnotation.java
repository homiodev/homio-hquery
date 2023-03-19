package org.touchhome.bundle.hquery.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HardwareRepositoryAnnotation {
    String stringValueOnDisable() default "unknown";

    int intValueOnDisable() default -1;

    boolean boolValueOnDisable() default false;

    // uses for logical description. Uses in toString() method
    String description() default "";
}
