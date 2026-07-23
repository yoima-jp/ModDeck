package com.yoima.moddeck.api.autoconfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoKeybind {
    boolean keyboard() default true;
    boolean mouse() default false;
    boolean modifiers() default false;
    boolean unbound() default true;
}
