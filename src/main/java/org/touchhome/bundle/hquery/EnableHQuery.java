package org.touchhome.bundle.hquery;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import({HQueryConfiguration.class})
@Documented
public @interface EnableHQuery {

    String scanBaseClassesPackage();
}
