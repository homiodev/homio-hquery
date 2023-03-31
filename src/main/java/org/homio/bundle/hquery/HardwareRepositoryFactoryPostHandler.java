package org.homio.bundle.hquery;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public interface HardwareRepositoryFactoryPostHandler {

    void accept(ConfigurableListableBeanFactory beanFactory);
}
