package org.homio.bundle.hquery.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(HardwareQueries.class)
public @interface HardwareQuery {

    String name();

    String[] value();

    int maxSecondsTimeout() default 60;

    // define directory from which should start process
    String dir() default "";

    boolean printOutput() default false;

    boolean ignoreOnError() default false;

    /**
     * Set this to true if you want parse error from commands. This value also set ignoreOnError as true
     * @return is redirect error input to regular input
     */
    boolean redirectErrorsToInputs() default false;

    String[] win() default "";

    // how long cache valid in sec
    int cacheValid() default 0;

    String valueOnError() default "";

    /**
     * Timeout to wait when error stream is closed before force stop it
     */
    int errorStreamWaitTimeoutMs() default 250;

    /**
     * Timeout to wait when input stream is closed before force stop it
     */
    int inputStreamWaitTimeoutMs() default 250;
}
