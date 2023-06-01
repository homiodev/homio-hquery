package org.homio.hquery.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ErrorsHandler {

    boolean throwError() default false;

    boolean logError() default true;

    // specify error to throw
    String onRetCodeError() default "";

    ErrorHandler[] errorHandlers() default {};

    String notRecognizeError() default "";

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ErrorHandler {

        String throwError();

        String onError();
    }
}
