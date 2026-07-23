package com.yoima.moddeck.api.autoconfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoEntry {
    String nameKey();
    String descriptionKey() default "";
    String category() default "general";
    String categoryKey() default "moddeck.category.general";
    int categoryOrder() default 0;
    int order() default 0;
    boolean requiresRestart() default false;
}
