package Map;

import java.util.ArrayList;

public class Mapping {
    private String className;
    private ArrayList<String> methodNames; // noms réels des méthodes Java
    private ArrayList<String> urls;        // URLs associées à chaque méthode

    public Mapping() {
        this.methodNames = new ArrayList<>();
        this.urls = new ArrayList<>();
    }

    public Mapping(String className) {
        this.className = className;
        this.methodNames = new ArrayList<>();
        this.urls = new ArrayList<>();
    }

    // Ajoute une méthode et son URL correspondante
    public void addMethod(String methodName, String url) {
        this.methodNames.add(methodName);
        this.urls.add(url);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    // Retourne le nom réel de la méthode correspondant à une URL
    public String getMethodByUrl(String url) {
        for (int i = 0; i < urls.size(); i++) {
            if (urls.get(i).equals(url)) {
                return methodNames.get(i);
            }
        }
        return null; // aucune méthode trouvée pour cette URL
    }

    public ArrayList<String> getMethodNames() {
        return methodNames;
    }

    public ArrayList<String> getUrls() {
        return urls;
    }
}
