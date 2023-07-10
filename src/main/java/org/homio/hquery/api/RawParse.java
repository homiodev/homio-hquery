package org.homio.hquery.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.List;
import org.springframework.lang.Nullable;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface RawParse {

    Class<? extends RawParseHandler> nix();

    Class<? extends RawParseHandler> win();

    interface RawParseHandler {

        Object handle(List<String> inputs, Field field);
    }
}
