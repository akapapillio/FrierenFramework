package com.frieren.p17;

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
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class FrontServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;
    private MyScanner controllerScanner;
    // Structure de mapping : URL -> (Verbe HTTP -> Méthode Java)
    private final Map<String, Map<String, Method>> urlToHttpMethod = new HashMap<>();
    // Cache pour les instances de contrôleurs
    private final Map<Method, Object> methodInstances = new HashMap<>();

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        controllerScanner = new MyScanner();
        initializeControllers();
    }

    private void initializeControllers() throws ServletException {
        try {
            String controllerPackage = getServletConfig().getInitParameter("Controllers");
            if (controllerPackage == null || controllerPackage.trim().isEmpty()) {
                throw new ServletException("Le paramètre 'Controllers' est manquant dans web.xml");
            }
            
            controllerScanner.scanControllersFromPackage(controllerPackage);
            
            for (Class<?> controllerClass : controllerScanner.getControllers()) {
                Controller controllerAnnotation = controllerClass.getAnnotation(Controller.class);
                String baseUrl = (controllerAnnotation != null) ? controllerAnnotation.value() : "";
                if (!baseUrl.startsWith("/")) baseUrl = "/" + baseUrl;
                if (baseUrl.equals("/")) baseUrl = "";

                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

                for (Method method : controllerClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(GetMapping.class)) {
                        String url = method.getAnnotation(GetMapping.class).value();
                        registerUrl(baseUrl + url, "GET", method, controllerInstance);
                    }
                    if (method.isAnnotationPresent(PostMapping.class)) {
                        String url = method.getAnnotation(PostMapping.class).value();
                        registerUrl(baseUrl + url, "POST", method, controllerInstance);
                    }
                    if (method.isAnnotationPresent(MyMap.class)) {
                        String url = method.getAnnotation(MyMap.class).url();
                        registerUrl(baseUrl + url, "GET", method, controllerInstance);
                        registerUrl(baseUrl + url, "POST", method, controllerInstance);
                    }
                }
            }
            System.out.println("🎯 " + methodInstances.size() + " méthodes de contrôleurs chargées.");
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'initialisation des contrôleurs", e);
        }
    }

    private void registerUrl(String url, String httpMethod, Method method, Object instance) {
        urlToHttpMethod.computeIfAbsent(url, k -> new HashMap<>())
                       .put(httpMethod.toUpperCase(), method);
        methodInstances.put(method, instance);
        System.out.println("🔗 Route enregistrée: [" + httpMethod + "] " + url);
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
            } else {
                String paramNameForLookup;
                String paramValue = null;

                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    paramNameForLookup = requestParam.value();
                    paramValue = req.getParameter(paramNameForLookup);
                }

                if (paramValue == null) {
                    paramNameForLookup = parameter.getName();
                    paramValue = pathVariables.get(paramNameForLookup);
                }
                
                if (paramValue == null) {
                    paramNameForLookup = parameter.getName();
                    paramValue = req.getParameter(paramNameForLookup);
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
