package org.homio.hquery;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public interface HQueryFactoryPostHandler {

  void accept(ConfigurableListableBeanFactory beanFactory);
}
