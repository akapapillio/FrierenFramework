package com.frieren.p17;

import annotation.Controller;
import annotation.MyMap;
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
    private Map<String, Class<?>> baseUrlToController;

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        controllerScanner = new MyScanner();
        baseUrlToController = new HashMap<>();

        initializeControllers();
    }

    private void initializeControllers() throws ServletException {
        try {
            // Le package des contrôleurs peut être externalisé dans web.xml
            String controllerPackage = getServletConfig().getInitParameter("Controllers");
            if (controllerPackage == null || controllerPackage.trim().isEmpty()) {
                throw new ServletException("Le paramètre 'Controllers' est manquant dans web.xml");
            }
            
            controllerScanner.scanControllersFromPackage(controllerPackage);
            
            for (Class<?> controller : controllerScanner.getControllers()) {
                Controller controllerAnnotation = controller.getAnnotation(Controller.class);
                if (controllerAnnotation == null) {
                    continue;
                }
                
                String baseUrl = controllerAnnotation.value();
                if (!baseUrl.startsWith("/")) {
                    baseUrl = "/" + baseUrl;
                }

                baseUrlToController.put(baseUrl, controller);
            }
            
            System.out.println("🎯 " + baseUrlToController.size() + " contrôleurs chargés");
            
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
        
        // Tente de servir une ressource statique en premier
        if (getServletContext().getResource(path) != null) {
            defaultServe(req, res);
        } else {
            // Sinon, traite comme une requête de contrôleur
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
                    if (actionPath.isEmpty()) {
                         actionPath = "/"; 
                    }
                    
                    Method targetMethod = findTargetMethod(controllerClass, actionPath);
                    
                    if (targetMethod != null) {
                        Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                        Object result = invokeMethodWithParams(targetMethod, controllerInstance, req, res);
                        handleControllerResult(result, req, res);
                        return;
                    }
                    
                    // Si aucune méthode ne correspond, afficher les infos du contrôleur pour le debug
                    displayControllerInfo(controllerClass, baseUrl, res);
                    return;
                }
            }

            // Si aucun contrôleur ne correspond
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try (PrintWriter out = res.getWriter()) {
                 out.println("<h1>404 Not Found</h1>");
                 out.println("<p>La ressource demandée n'a pas été trouvée : <strong>" + path + "</strong></p>");
            }
            
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
                 out.println("<h1>500 Internal Server Error</h1>");
                 out.println("<p>Erreur interne du serveur: " + e.getMessage() + "</p>");
                 e.printStackTrace(out);
            }
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
    
    private Method findTargetMethod(Class<?> controllerClass, String actionPath) {
        for (Method method : controllerClass.getDeclaredMethods()) {
            MyMap mapping = method.getAnnotation(MyMap.class);
            if (mapping != null && mapping.url().equals(actionPath)) {
                return method;
            }
        }
        return null;
    }

    private Object invokeMethodWithParams(Method method, Object controllerInstance, HttpServletRequest req, HttpServletResponse res) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        Object[] args = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            java.lang.reflect.Parameter parameter = parameters[i];
            String paramName = parameter.getName();
            
            if (paramType.equals(HttpServletRequest.class)) {
                args[i] = req;
            } else if (paramType.equals(HttpServletResponse.class)) {
                args[i] = res;
            } else {
                String paramValue = req.getParameter(paramName);
                
                if (paramValue == null || paramValue.trim().isEmpty()) {
                    if (paramType.isPrimitive()) {
                        throw new IllegalArgumentException("Paramètre primitif requis manquant: " + paramName + " (type: " + paramType.getSimpleName() + ")");
                    }
                    args[i] = null;
                } else {
                    args[i] = convertParameterValue(paramValue, paramType);
                }
            }
        }
        
        return method.invoke(controllerInstance, args);
    }

    private Object convertParameterValue(String value, Class<?> targetType) {
        try {
            if (targetType.equals(String.class)) {
                return value;
            } else if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
                return Integer.parseInt(value);
            } else if (targetType.equals(long.class) || targetType.equals(Long.class)) {
                return Long.parseLong(value);
            } else if (targetType.equals(double.class) || targetType.equals(Double.class)) {
                return Double.parseDouble(value);
            } else if (targetType.equals(boolean.class) || targetType.equals(Boolean.class)) {
                return Boolean.parseBoolean(value);
            } else {
                throw new IllegalArgumentException("Type de paramètre non supporté pour la conversion: " + targetType.getName());
            }
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
                RequestDispatcher dispatcher = req.getRequestDispatcher("/WEB-INF/views/" + viewOrContent);
                dispatcher.forward(req, res); 
            } else {
                res.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = res.getWriter()) {
                    out.println(viewOrContent); 
                }
            }
        } else if (result instanceof ModelView) {
            ModelView mv = (ModelView) result;
            for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                req.setAttribute(entry.getKey(), entry.getValue());
            }
            String viewPath = "/WEB-INF/views/" + mv.getView();
            RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
            dispatcher.forward(req, res);
        } else {
            res.setContentType("text/plain;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                 out.println("Type de retour non géré : " + result.getClass().getName());
            }
        }
    }
    
    private boolean isViewName(String result) {
        return result.endsWith(".jsp") || result.endsWith(".html");
    }
    
    private boolean isViewName(String result) {
        return result.endsWith(".jsp") || result.endsWith(".html");
    }

    private void displayControllerInfo(Class<?> controllerClass, String baseUrl, HttpServletResponse res) throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<h2>Controller: " + controllerClass.getSimpleName() + ".class</h2>");
            out.println("<p>Base URL: " + baseUrl + "</p>");
            out.println("<h3>Méthodes supportées :</h3>");
            out.println("<ul>");
    
            for (Method method : controllerClass.getDeclaredMethods()) {
                MyMap mapping = method.getAnnotation(MyMap.class);
                if (mapping != null) {
                    out.println("<li>" + method.getName() + "() ➜ " + mapping.url() + "</li>");
                }
            }
    
            out.println("</ul>");
        }
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
        return method.invoke(controllerInstance, args);
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
