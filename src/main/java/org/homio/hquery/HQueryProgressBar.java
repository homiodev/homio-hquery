package org.homio.hquery;

import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public abstract class HQueryProgressBar {

    private final double min;
    private final double max;
    private final double expectedTimeToExecute;

    public abstract void progress(double value, String message, boolean isError);

    public static HQueryProgressBar of(Consumer<String> consumer) {
        return new HQueryProgressBar(0, 0, 0) {
            @Override
            public void progress(double value, String message, boolean isError) {
                consumer.accept(message);
            }
        };
    }
}
