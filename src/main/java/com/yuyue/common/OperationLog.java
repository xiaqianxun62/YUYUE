package com.yuyue.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 方法上，由 OperationLogAspect 统一记录
 * 「操作人 / 动作 / 入参摘要 / 结果 / 耗时」到日志文件。
 *
 * <p>敏感字段（password / token 等）在切面中自动脱敏；文件上传等大对象只记录摘要。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作描述，如「报名」「取消报名」 */
    String value();

    /** 是否记录方法入参摘要（默认记录，登录等含密码的接口也安全：会自动脱敏） */
    boolean logArgs() default true;
}
