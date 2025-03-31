package org.homio.hquery;

import jakarta.annotation.Nullable;
import java.util.function.BiConsumer;

public interface ProgressBar extends BiConsumer<Double, String> {

  void progress(double progress, @Nullable String message, boolean error);

  default void progress(double progress, @Nullable String message) {
    progress(progress, message, false);
  }

  default void done() {
    progress(100, "Done");
  }

  @Override
  default void accept(Double progress, @Nullable String message) {
    progress(progress, message);
  }

  default boolean isCancelled() {
    return false;
  }

  default void onCancel(Runnable handler) {}

  default void logToConsole(boolean value) {}
}
