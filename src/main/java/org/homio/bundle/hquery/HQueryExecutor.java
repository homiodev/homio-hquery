package org.homio.bundle.hquery;

import java.io.File;
import org.homio.bundle.hquery.api.HardwareQuery;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

public interface HQueryExecutor {

    String[] getValues(HardwareQuery hardwareQuery);

    default String updateCommand(String cmd) {
        return cmd;
    }

    Process createProcess(String[] cmdParts, String[] env, File dir);

    void prepare(ConfigurableListableBeanFactory beanFactory, Environment env);
}
