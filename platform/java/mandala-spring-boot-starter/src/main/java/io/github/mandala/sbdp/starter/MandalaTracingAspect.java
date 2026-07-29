package io.github.mandala.sbdp.starter;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;

/**
 * Adds stable, Mandala-specific attributes to application service and Doma DAO spans.
 * DAO detection intentionally uses the conventional {@code *Dao}/{@code *DaoImpl} names so
 * consumers do not need a compile-time dependency on Doma in this starter.
 */
@Aspect
public final class MandalaTracingAspect {
    static final AttributeKey<String> LAYER = AttributeKey.stringKey("mandala.layer");
    static final AttributeKey<String> STABLE_ID = AttributeKey.stringKey("mandala.stable_id");
    static final AttributeKey<String> JAVA_CLASS = AttributeKey.stringKey("mandala.java.class");
    static final AttributeKey<String> JAVA_METHOD = AttributeKey.stringKey("mandala.java.method");

    private final Tracer tracer;

    public MandalaTracingAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@within(io.github.mandala.sbdp.starter.MandalaApplicationService) || "
            + "@annotation(io.github.mandala.sbdp.starter.MandalaApplicationService)")
    public Object traceApplicationService(ProceedingJoinPoint joinPoint) throws Throwable {
        return trace(joinPoint, "application_service", "java");
    }

    @Around("execution(public * *..*Dao.*(..)) || execution(public * *..*DaoImpl.*(..))")
    public Object traceDomaDao(ProceedingJoinPoint joinPoint) throws Throwable {
        return trace(joinPoint, "doma_dao", "dao");
    }

    private Object trace(ProceedingJoinPoint joinPoint, String layer, String stableIdPrefix)
            throws Throwable {
        Method signatureMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget() == null
                ? signatureMethod.getDeclaringClass()
                : AopUtils.getTargetClass(joinPoint.getTarget());
        Method method = AopUtils.getMostSpecificMethod(signatureMethod, targetClass);
        String className = userFacingClassName(targetClass, signatureMethod.getDeclaringClass());
        String methodName = method.getName();
        String stableId = stableIdPrefix + ":" + className + "#" + methodName
                + canonicalParameterSignature(method);

        Span span = tracer.spanBuilder("mandala." + layer + " "
                        + simpleName(className) + "." + methodName)
                .setAttribute(LAYER, layer)
                .setAttribute(STABLE_ID, stableId)
                .setAttribute(JAVA_CLASS, className)
                .setAttribute(JAVA_METHOD, methodName)
                .setAttribute("code.namespace", className)
                .setAttribute("code.function", methodName)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return joinPoint.proceed();
        } catch (Throwable failure) {
            span.recordException(failure);
            span.setStatus(StatusCode.ERROR, failure.getClass().getSimpleName());
            throw failure;
        } finally {
            span.end();
        }
    }

    private static String userFacingClassName(Class<?> targetClass, Class<?> declaredClass) {
        String targetName = targetClass.getName();
        if (targetName.contains("$$") || targetName.endsWith("Impl")) {
            for (Class<?> candidate : targetClass.getInterfaces()) {
                if (candidate.getSimpleName().endsWith("Dao")) {
                    return candidate.getName();
                }
            }
        }
        return targetName.contains("$$") ? declaredClass.getName() : targetName;
    }

    private static String simpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static String canonicalParameterSignature(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        StringBuilder signature = new StringBuilder("(");
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) signature.append(',');
            Class<?> parameter = parameters[index];
            boolean varArgs = method.isVarArgs() && index == parameters.length - 1;
            if (varArgs) parameter = parameter.getComponentType();
            signature.append(canonicalType(parameter));
            if (varArgs) signature.append("...");
        }
        return signature.append(')').toString();
    }

    private static String canonicalType(Class<?> type) {
        if (type.isArray()) return canonicalType(type.getComponentType()) + "[]";
        return type.isPrimitive() ? type.getName() : type.getSimpleName();
    }
}
