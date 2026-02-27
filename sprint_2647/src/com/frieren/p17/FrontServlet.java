import annotation.MyMap;
import annotation.PostMapping;
import annotation.RequestParam;
import annotation.ResponseBody;
import annotation.RestController;
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
import com.google.gson.Gson;

public class FrontServlet extends HttpServlet {

    private final Map<String, Map<String, Method>> urlToHttpMethod = new HashMap<>();
    private final Map<Method, Object> methodInstances = new HashMap<>();
    private final Map<Class<?>, Object> singletons = new HashMap<>();
    private final Gson gson = new Gson();

    @Override
    public void init() throws ServletException {
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
                injectDependencies(controllerInstance);

                String baseUrl = "";
                if (controllerClass.isAnnotationPresent(Controller.class)) {
                    baseUrl = controllerClass.getAnnotation(Controller.class).value();
                } else if (controllerClass.isAnnotationPresent(RestController.class)) {
                    baseUrl = controllerClass.getAnnotation(RestController.class).value();
                }

                if (!baseUrl.startsWith("/")) baseUrl = "/" + baseUrl;
                if (baseUrl.equals("/")) baseUrl = "";


        try {
            Object controllerInstance = methodInstances.get(targetMethod);
            Object result = invokeMethodWithParams(targetMethod, controllerInstance, req, res, pathParams);
            handleControllerResult(result, req, res, targetMethod);
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = res.getWriter()) {
        }
    }

    public void handleControllerResult(Object result, HttpServletRequest req, HttpServletResponse res, Method method) throws Exception {
        if (result == null) {
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        boolean isJson = method.isAnnotationPresent(ResponseBody.class) ||
                         method.getDeclaringClass().isAnnotationPresent(RestController.class);

        if (isJson) {
            res.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = res.getWriter()) {
                out.print(gson.toJson(result));
            }
            return;
        }
        
        if (result instanceof String) {
            String viewOrContent = (String) result;

