package org.touchhome.bundle.hquery;

import org.touchhome.bundle.hquery.api.HardwareQuery;

import java.io.File;

public interface HQueryExecutor {
    String[] getValues(HardwareQuery hardwareQuery);

    default String updateCommand(String cmd) {
        return cmd;
    }

    Process createProcess(String[] cmdParts, String[] env, File dir);
}
