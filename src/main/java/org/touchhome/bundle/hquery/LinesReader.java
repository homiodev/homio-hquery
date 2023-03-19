package org.touchhome.bundle.hquery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class LinesReader implements Runnable {

    private final String name;
    private final InputStream inputStream;
    private final Consumer<String> lineConsumer;
    private final BiConsumer<Double, String> progressBar;

    public LinesReader(@NotNull String name, @NotNull InputStream inputStream, @Nullable BiConsumer<Double, String> progressBar,
                       @NotNull Consumer<String> lineConsumer) {
        this.name = name;
        this.inputStream = inputStream;
        this.lineConsumer = lineConsumer;
        this.progressBar = progressBar != null ? progressBar : (progress, message) -> {
        };
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineConsumer.accept(line);
                progressBar.accept(-1D, line);
            }
        } catch (IOException ex) {
            lineConsumer.accept("Thread reader <" + name + "> got error: <" + ex.getMessage() + ">");
            throw new RuntimeException(ex);
        }
    }
}
