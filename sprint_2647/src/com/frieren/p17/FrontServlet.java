package com.frieren.p17;

import annotation.Autowired;
import annotation.Controller;
import annotation.GetMapping;
import annotation.MyMap;
import annotation.PostMapping;
import annotation.RequestParam;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class FrontServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;
    private MyScanner scanner;
    private final Map<String, Map<String, Method>> urlToHttpMethod = new HashMap<>();
    private final Map<Method, Object> methodInstances = new HashMap<>();
    private final Map<Class<?>, Object> singletons = new HashMap<>();

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        scanner = new MyScanner();
        initializeFramework();
    }

    private void initializeFramework() throws ServletException {
        try {
            String basePackage = getServletConfig().getInitParameter("Controllers");
            if (basePackage == null || basePackage.trim().isEmpty()) {
                throw new ServletException("Le paramètre 'Controllers' (package de base) est manquant dans web.xml");
            }
            
            scanner.scanPackage(basePackage);

            for (Class<?> componentClass : scanner.getComponents()) {
                Object instance = componentClass.getDeclaredConstructor().newInstance();
                singletons.put(componentClass, instance);
            }

            for (Class<?> controllerClass : scanner.getControllers()) {
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                injectDependencies(controllerInstance);

                Controller controllerAnnotation = controllerClass.getAnnotation(Controller.class);
                String baseUrl = (controllerAnnotation != null) ? controllerAnnotation.value() : "";
                if (!baseUrl.startsWith("/")) baseUrl = "/" + baseUrl;
                if (baseUrl.equals("/")) baseUrl = "";

                for (Method method : controllerClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(GetMapping.class)) {
                        registerUrl(baseUrl + method.getAnnotation(GetMapping.class).value(), "GET", method, controllerInstance);
                    }
                    if (method.isAnnotationPresent(PostMapping.class)) {
                        registerUrl(baseUrl + method.getAnnotation(PostMapping.class).value(), "POST", method, controllerInstance);
                    }
                    if (method.isAnnotationPresent(MyMap.class)) {
                        String urlPath = method.getAnnotation(MyMap.class).url();
                        registerUrl(baseUrl + urlPath, "GET", method, controllerInstance);
                        registerUrl(baseUrl + urlPath, "POST", method, controllerInstance);
                    }
                }
            }
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'initialisation du framework", e);
        }
    }

    private void injectDependencies(Object targetInstance) throws IllegalAccessException {
        for (Field field : targetInstance.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                Object dependency = findSingletonForType(field.getType());
                if (dependency != null) {
                    field.setAccessible(true);
                    field.set(targetInstance, dependency);
                }
            }
        }
    }

    private Object findSingletonForType(Class<?> type) {
        for (Map.Entry<Class<?>, Object> entry : singletons.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void registerUrl(String url, String httpMethod, Method method, Object instance) {
        urlToHttpMethod.computeIfAbsent(url, k -> new HashMap<>()).put(httpMethod.toUpperCase(), method);
        methodInstances.put(method, instance);
    }

    private void registerUrl(String url, String httpMethod, Method method, Object instance) {
        urlToHttpMethod.computeIfAbsent(url, k -> new HashMap<>()).put(httpMethod.toUpperCase(), method);
        methodInstances.put(method, instance);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (getServletContext().getResource(path) != null && !path.equals("/")) {
            defaultServe(req, res);
        } else {
            customServe(req, res, path);
        }
    }
    
    private void customServe(HttpServletRequest req, HttpServletResponse res, String path) throws IOException {
        String httpMethod = req.getMethod().toUpperCase();
        Method targetMethod = null;
        Map<String, String> pathParams = new HashMap<>();

        for (Map.Entry<String, Map<String, Method>> entry : urlToHttpMethod.entrySet()) {
            String pattern = entry.getKey();
            if (UrlMatcher.matches(pattern, path)) {
                Map<String, Method> methodMap = entry.getValue();
                targetMethod = methodMap.get(httpMethod);
                if (targetMethod != null) {
                    pathParams = UrlMatcher.extractParameters(pattern, path);
                    break;
                }
            }
        }

        if (targetMethod == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try (PrintWriter out = res.getWriter()) {
                 out.println("<h1>404 Not Found</h1><p>Aucune route ne correspond à l'URL '" + path + "' pour la méthode " + httpMethod + ".</p>");
            }
            return;
        }

        try {
            Object controllerInstance = methodInstances.get(targetMethod);
            Object result = invokeMethodWithParams(targetMethod, controllerInstance, req, res, pathParams);
            handleControllerResult(result, req, res);
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                 out.println("<h1>500 Internal Server Error</h1><p>Erreur interne du serveur: " + e.getMessage() + "</p>");
                 e.printStackTrace(out);
            }
        }
    }

    private Object invokeMethodWithParams(Method method, Object controllerInstance, HttpServletRequest req, HttpServletResponse res, Map<String, String> pathVariables) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            java.lang.reflect.Parameter parameter = parameters[i];
            
            if (paramType.equals(HttpServletRequest.class)) {
                args[i] = req;
            } else if (paramType.equals(HttpServletResponse.class)) {
                args[i] = res;
            } else if (paramType.equals(MySession.class)) {
                args[i] = new MySession(req.getSession());
            } else {
                String paramValue = null;
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    paramValue = req.getParameter(requestParam.value());
                }
                if (paramValue == null) {
                    paramValue = pathVariables.get(parameter.getName());
                }
                if (paramValue == null) {
                    paramValue = req.getParameter(parameter.getName());
                }
                
                if (paramValue == null || paramValue.trim().isEmpty()) {
                    if (paramType.isPrimitive()) {
                        throw new IllegalArgumentException("Paramètre primitif requis manquant: " + parameter.getName());
                    }
                    args[i] = null;
                } else {
                    args[i] = convertParameterValue(paramValue, paramType);
                }
            }
        }
        return method.invoke(controllerInstance, args);
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }

    private Object convertParameterValue(String value, Class<?> targetType) {
        try {
            if (targetType.equals(String.class)) return value;
            if (targetType.equals(int.class) || targetType.equals(Integer.class)) return Integer.parseInt(value);
            if (targetType.equals(long.class) || targetType.equals(Long.class)) return Long.parseLong(value);
            if (targetType.equals(double.class) || targetType.equals(Double.class)) return Double.parseDouble(value);
            if (targetType.equals(boolean.class) || targetType.equals(Boolean.class)) return Boolean.parseBoolean(value);
            throw new IllegalArgumentException("Type de paramètre non supporté pour la conversion: " + targetType.getName());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Erreur de conversion pour la valeur '" + value + "' vers le type " + targetType.getSimpleName(), e);
        }
    }

    public void handleControllerResult(Object result, HttpServletRequest req, HttpServletResponse res) throws Exception {
        if (result == null) {
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        
        if (result instanceof String) {
            String viewOrContent = (String) result;
            if (isViewName(viewOrContent)) {
                req.getRequestDispatcher("/WEB-INF/views/" + viewOrContent).forward(req, res); 
            } else {
                res.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = res.getWriter()) { out.println(viewOrContent); }
            }
        } else if (result instanceof ModelView) {
            ModelView mv = (ModelView) result;
            mv.getData().forEach(req::setAttribute);
            req.getRequestDispatcher("/WEB-INF/views/" + mv.getView()).forward(req, res);
        } else {
            res.setContentType("text/plain;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) { out.println("Type de retour non géré : " + result.getClass().getName()); }
        }
    }
    
    private boolean isViewName(String result) {
        return result.endsWith(".jsp") || result.endsWith(".html");
    }
}
