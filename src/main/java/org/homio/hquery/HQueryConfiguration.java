package org.homio.hquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

@Configuration
public class HQueryConfiguration implements ImportAware {

  private AnnotationAttributes scanBaseClassesPackage;

  @Override
  public void setImportMetadata(AnnotationMetadata metadata) {
    scanBaseClassesPackage =
        AnnotationAttributes.fromMap(
            metadata.getAnnotationAttributes(EnableHQuery.class.getName()));
  }

  @Bean
  public BeanFactoryPostProcessor beanFactoryPostProcessor(
      @Autowired HQueryLogger logger,
      @Autowired(required = false) HQueryFactoryPostHandler handler) {
    return new HQueryFactoryPostProcessor(
        scanBaseClassesPackage.getString("scanBaseClassesPackage"), handler, logger);
  }
}
