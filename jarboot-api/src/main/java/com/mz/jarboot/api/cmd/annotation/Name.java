package com.mz.jarboot.api.cmd.annotation;

/**
 * Name 名字
 * @author majianzheng
 */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE})
public @interface Name {
    java.lang.String value();
}
