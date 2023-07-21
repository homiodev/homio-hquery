package org.homio.hquery;

import org.homio.hquery.api.HardwareQuery;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

public interface HQueryExecutor {

    String[] getValues(HardwareQuery hardwareQuery);

    default String updateCommand(String cmd) {
        return cmd;
    }

    void prepare(ConfigurableListableBeanFactory beanFactory, Environment env);
}
