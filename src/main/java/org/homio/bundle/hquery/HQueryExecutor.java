package org.homio.bundle.hquery;

import java.io.File;
import org.homio.bundle.hquery.api.HardwareQuery;

public interface HQueryExecutor {

    String[] getValues(HardwareQuery hardwareQuery);

    default String updateCommand(String cmd) {
        return cmd;
    }

    Process createProcess(String[] cmdParts, String[] env, File dir);
}
