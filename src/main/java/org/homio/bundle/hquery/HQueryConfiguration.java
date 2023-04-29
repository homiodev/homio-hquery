package org.homio.bundle.hquery;

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
        scanBaseClassesPackage = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(EnableHQuery.class.getName()));
    }

    @Bean
    public BeanFactoryPostProcessor beanFactoryPostProcessor(@Autowired(required = false) HardwareRepositoryFactoryPostHandler handler) {
        return new HardwareRepositoryFactoryPostProcessor(
            scanBaseClassesPackage.getString("scanBaseClassesPackage"),
            handler);
    }
}
