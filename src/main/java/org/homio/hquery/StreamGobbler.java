package org.homio.hquery;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
@RequiredArgsConstructor
public class StreamGobbler {

    private final @NotNull String name;
    private final @NotNull Consumer<String> inputConsumer;
    private final @NotNull Consumer<String> errorConsumer;
    private Future<?> inputFuture = null;
    private Future<?> errorFuture = null;
    private ExecutorService executorService;

    public static void streamAndStop(Process process, int waitTimeoutBeforeStopMs, int waitStopStreamsTimeoutMs) {
        StreamGobbler killStreamGobbler = new StreamGobbler("kill-sig-" + process.pid(), log::info, log::error);
        killStreamGobbler.stream(process);
        killStreamGobbler.stopStream(waitTimeoutBeforeStopMs, waitStopStreamsTimeoutMs);
    }

    public void stream(Process process) {
        executorService = Executors.newFixedThreadPool(2);
        errorFuture = executorService.submit(new StreamReader(name + "/error stream reader",
            process.getErrorStream(), errorConsumer));
        inputFuture = executorService.submit(new StreamReader(name + "/input stream reader",
            process.getInputStream(), inputConsumer));
    }

    @SneakyThrows
    public void stopStream(int waitTimeoutBeforeStopMs, int waitStopStreamsTimeoutMs) {
        if (executorService != null) {
            if (waitTimeoutBeforeStopMs > 0) {
                try {
                    errorFuture.get(waitTimeoutBeforeStopMs, TimeUnit.MILLISECONDS);
                    inputFuture.get(waitTimeoutBeforeStopMs, TimeUnit.MILLISECONDS);
                } catch (Exception ignore) {
                }
            }
            errorFuture.cancel(true);
            inputFuture.cancel(true);
            executorService.shutdown();
            if (!executorService.awaitTermination(waitStopStreamsTimeoutMs, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        }
    }
}
