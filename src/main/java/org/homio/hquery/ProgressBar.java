package org.homio.hquery;

import java.util.function.BiConsumer;

public interface ProgressBar extends BiConsumer<Double, String> {

    void progress(double progress, String message, boolean error);

    default void progress(double progress, String message) {
        progress(progress, message, false);
    }

    default void done() {
        progress(100, "Done");
    }

    @Override
    default void accept(Double progress, String message) {
        progress(progress, message);
    }
}
