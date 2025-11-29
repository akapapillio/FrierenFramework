package com.frieren.p17;

import annotation.Controller;
import annotation.MyMap;
import annotation.RequestParam;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FrontServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;
    private MyScanner controllerScanner;
    private Map<String, Class<?>> baseUrlToController;

    // Classe interne pour encapsuler le résultat d'une correspondance de route
    private static class RouteMatch {
        private final Method method;
        private final Map<String, String> pathVariables;

        public RouteMatch(Method method, Map<String, String> pathVariables) {
            this.method = method;
            this.pathVariables = pathVariables;
        }

        public Method getMethod() { return method; }
        public Map<String, String> getPathVariables() { return pathVariables; }
    }

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        controllerScanner = new MyScanner();
        baseUrlToController = new HashMap<>();
        initializeControllers();
    }

    private void initializeControllers() throws ServletException {
        try {
            String controllerPackage = getServletConfig().getInitParameter("Controllers");
            if (controllerPackage == null || controllerPackage.trim().isEmpty()) {
                throw new ServletException("Le paramètre 'Controllers' est manquant dans web.xml");
            }
            
            controllerScanner.scanControllersFromPackage(controllerPackage);
            
            for (Class<?> controller : controllerScanner.getControllers()) {
                Controller controllerAnnotation = controller.getAnnotation(Controller.class);
                if (controllerAnnotation == null) continue;
                
                String baseUrl = controllerAnnotation.value();
                if (!baseUrl.startsWith("/")) baseUrl = "/" + baseUrl;
                
                baseUrlToController.put(baseUrl, controller);
            }
            System.out.println("🎯 " + baseUrlToController.size() + " contrôleurs chargés");
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'initialisation des contrôleurs", e);
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (getServletContext().getResource(path) != null) {
            defaultServe(req, res);
        } else {
            customServe(req, res);
        }
    }
    
    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String path = req.getRequestURI().substring(req.getContextPath().length());
            
            for (Map.Entry<String, Class<?>> entry : baseUrlToController.entrySet()) {
                String baseUrl = entry.getKey();
                
                if (path.startsWith(baseUrl)) {
                    Class<?> controllerClass = entry.getValue();
                    String actionPath = path.substring(baseUrl.length());
                    if (actionPath.isEmpty()) actionPath = "/"; 
                    
                    RouteMatch match = findRouteMatch(controllerClass, actionPath);
                    
                    if (match != null) {
                        Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                        Object result = invokeMethodWithParams(match.getMethod(), controllerInstance, req, res, match.getPathVariables());
                        handleControllerResult(result, req, res);
                        return;
                    }
                    
                    displayControllerInfo(controllerClass, baseUrl, res);
                    return;
                }
            }

            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try (PrintWriter out = res.getWriter()) {
                 out.println("<h1>404 Not Found</h1><p>La ressource demandée n'a pas été trouvée : <strong>" + path + "</strong></p>");
            }
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                 out.println("<h1>500 Internal Server Error</h1><p>Erreur interne du serveur: " + e.getMessage() + "</p>");
                 e.printStackTrace(out);
            }
        }
    }

    private RouteMatch findRouteMatch(Class<?> controllerClass, String requestPath) {
        for (Method method : controllerClass.getDeclaredMethods()) {
            MyMap mapping = method.getAnnotation(MyMap.class);
            if (mapping != null) {
                String pattern = mapping.url();
                if (UrlMatcher.matches(pattern, requestPath)) {
                    Map<String, String> pathVars = UrlMatcher.extractParameters(pattern, requestPath);
                    return new RouteMatch(method, pathVars);
                }
            }
        }
        return null;
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

                // Priorité 1: @RequestParam
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    paramNameForLookup = requestParam.value();
                    paramValue = req.getParameter(paramNameForLookup);
                }

                // Priorité 2: Variable de chemin (si @RequestParam n'a rien donné)
                if (paramValue == null) {
                    paramNameForLookup = parameter.getName();
                    paramValue = pathVariables.get(paramNameForLookup);
                }
                
                // Priorité 3: Paramètre de requête par nom (si rien d'autre n'a fonctionné)
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

    private void displayControllerInfo(Class<?> controllerClass, String baseUrl, HttpServletResponse res) throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<h2>Controller: " + controllerClass.getSimpleName() + ".class</h2>");
            out.println("<p>Base URL: " + baseUrl + "</p>");
            out.println("<h3>Méthodes supportées :</h3><ul>");
            for (Method method : controllerClass.getDeclaredMethods()) {
                MyMap mapping = method.getAnnotation(MyMap.class);
                if (mapping != null) {
                    out.println("<li>" + method.getName() + "() ➜ " + mapping.url() + "</li>");
                }
            }
            out.println("</ul>");
        }
    }
}
