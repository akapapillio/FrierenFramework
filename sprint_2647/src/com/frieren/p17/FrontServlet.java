package com.frieren.p17;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import Map.HashMap;
import Map.Mapping;
import annotation.AnnotationController;
import annotation.GetMethode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontServlet extends HttpServlet {

    private RequestDispatcher defaultDispatcher;

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        List<HashMap> routes = new ArrayList<>();

        try {
            String controllerPackage = getServletConfig().getInitParameter("Controllers");
            if (controllerPackage == null) return;

            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            URL packageUrl = loader.getResource(controllerPackage.replace('.', '/'));
            if (packageUrl == null) return;

            File dir = new File(URLDecoder.decode(packageUrl.getFile(), "UTF-8"));
            if (!dir.exists() || !dir.isDirectory()) return;

            for (File file : dir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".class")) {
                    String className = file.getName().replace(".class", "");
                    Class<?> clazz = Class.forName(controllerPackage + "." + className);

                    if (clazz.isAnnotationPresent(AnnotationController.class)) {
                        String basePath = "/" + clazz.getAnnotation(AnnotationController.class).value();
                        Mapping mapping = new Mapping(clazz.getName());

                        for (Method method : clazz.getDeclaredMethods()) {
                            if (method.isAnnotationPresent(GetMethode.class)) {
                                String url = basePath + method.getAnnotation(GetMethode.class).value();
                                mapping.addMethod(method.getName(), url);
                                routes.add(new HashMap(url, mapping));
                            }
                        }
                    }
                }
            }

            // Stockage global dans le ServletContext
            getServletContext().setAttribute("routes", routes);
            System.out.println("✅ Scan terminé : " + routes.size() + " routes enregistrées dans ServletContext");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        // Endpoint spécial pour afficher toutes les routes
        if (path.equals("/routes")) {
            showRoutes(res);
            return;
        }

        // Vérifier si c'est un fichier statique
        boolean resourceExists = getServletContext().getResource(path) != null;
        if (resourceExists) {
            defaultServe(req, res);
        } else {
            // Vérifie les routes annotées
            if (!handleAnnotatedControllers(req, res, path)) {
                customServe(req, res);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean handleAnnotatedControllers(HttpServletRequest req, HttpServletResponse res, String path) {
        try {
            List<HashMap> routes = (List<HashMap>) getServletContext().getAttribute("routes");
            if (routes == null) return false;

            for (HashMap h : routes) {
                if (h.getUrl().equals(path) && h.isAssociated()) {
                    Mapping mapping = h.getMapping();
                    String className = mapping.getClassName();
                    String methodName = h.leMethode();

                    Class<?> clazz = Class.forName(className);
                    Object controllerInstance = clazz.getDeclaredConstructor().newInstance();
                    Method method = clazz.getDeclaredMethod(methodName);
                    Object result = method.invoke(controllerInstance);

                    res.setContentType("text/html;charset=UTF-8");
                    try (PrintWriter out = res.getWriter()) {
                        out.println(result);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    // Affichage des routes pour debug
    @SuppressWarnings("unchecked")
    private void showRoutes(HttpServletResponse res) throws IOException {
        List<HashMap> routes = (List<HashMap>) getServletContext().getAttribute("routes");

        res.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.println("<html><head><title>Routes</title></head><body>");
            out.println("<h1>Liste des routes scannées</h1>");
            out.println("<ul>");

            if (routes != null) {
                for (HashMap h : routes) {
                    Mapping mapping = h.getMapping();
                    String className = mapping.getClassName();
                    String methodName = h.leMethode();
                    String url = h.getUrl();

                    out.printf("<li>URL: <strong>%s</strong> → Classe: <strong>%s</strong>, Méthode: <strong>%s</strong></li>",
                            url, className, methodName);
                }
            }

            out.println("</ul></body></html>");
        }
    }

    private void customServe(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (PrintWriter out = res.getWriter()) {
            String uri = req.getRequestURI();
            String responseBody = """
                    <html>
                        <head><title>Resource Not Found</title></head>
                        <body>
                            <h1>Unknown resource</h1>
                            <p>The requested URL was not found: <strong>%s</strong></p>
                        </body>
                    </html>
                    """.formatted(uri);

            res.setContentType("text/html;charset=UTF-8");
            out.println(responseBody);
        }
    }

    private void defaultServe(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        defaultDispatcher.forward(req, res);
    }
}
