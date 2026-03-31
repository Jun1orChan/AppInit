package com.nd.appinit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记参与 Application 生命周期分发的监听器类。
 * 被标记的类必须实现 {@link com.nd.appinit.IAppInitListener} 接口。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface AppInit {

    /**
     * 优先级，数值越小越先执行，默认 Integer.MAX_VALUE。
     */
    int priority() default Integer.MAX_VALUE;
}