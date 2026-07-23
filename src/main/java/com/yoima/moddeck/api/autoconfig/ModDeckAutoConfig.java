package com.yoima.moddeck.api.autoconfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModDeckAutoConfig {
    String modId();
    String titleKey();
    String descriptionKey() default "";
}
