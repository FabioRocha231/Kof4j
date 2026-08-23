package dev.kof.compiler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;


public final class ReflectiveHandler implements KofHttpServer.Handler {

    private final Method handle5;
    private final Method handle4;
    private final Method handle3;
    private final Method handle0;
    private final Method[] methodHandlers;

    private ReflectiveHandler(Method handle5, Method handle4, Method handle3,
                              Method handle0, Method[] methodHandlers) {
        this.handle5 = handle5;
        this.handle4 = handle4;
        this.handle3 = handle3;
        this.handle0 = handle0;
        this.methodHandlers = methodHandlers;
    }

    public static ReflectiveHandler forClass(Class<?> clazz) {
        Method h5 = null;
        Method h4 = null;
        Method h3 = null;
        Method h0 = null;
        java.util.List<Method> perMethod = new java.util.ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals("handle") && !isHttpMethodName(m.getName())) continue;
            Class<?>[] params = m.getParameterTypes();
            boolean allStrings = true;
            for (Class<?> p : params) {
                if (p != String.class) {
                    allStrings = false;
                    break;
                }
            }
            if (m.getName().equals("handle") && allStrings && params.length == 5) h5 = m;
            else if (m.getName().equals("handle") && allStrings && params.length == 4) h4 = m;
            else if (m.getName().equals("handle") && allStrings && params.length == 3) h3 = m;
            else if (m.getName().equals("handle") && params.length == 0) h0 = m;
            else if (isHttpMethodName(m.getName()) && params.length == 0) perMethod.add(m);
        }
        return new ReflectiveHandler(h5, h4, h3, h0, perMethod.toArray(new Method[0]));
    }

    private static boolean isHttpMethodName(String name) {
        return name.equals("get") || name.equals("post") || name.equals("put")
                || name.equals("delete") || name.equals("patch") || name.equals("options");
    }

    private Object invoke(Method m, Object... args) throws Exception {
        if (m == null) return null;
        if (!Modifier.isStatic(m.getModifiers())) return null;
        return m.invoke(null, args);
    }

    @Override
    public String handle(String method, String path, String body, String query, String headers) {
        try {
            Object result;
            if (handle5 != null) {
                result = invoke(handle5, method, path, body, query, headers);
            } else if (handle4 != null) {
                result = invoke(handle4, method, path, body, query);
            } else if (handle3 != null) {
                result = invoke(handle3, method, path, body);
            } else if (handle0 != null) {
                result = invoke(handle0);
            } else {
                for (Method m : methodHandlers) {
                    if (m.getName().equals(method.toLowerCase())) {
                        result = invoke(m);
                        return result instanceof String s ? s : null;
                    }
                }
                return null;
            }
            return result instanceof String s ? s : null;
        } catch (Exception e) {
            throw new RuntimeException("handler invocation failed: " + e.getMessage(), e);
        }
    }
}