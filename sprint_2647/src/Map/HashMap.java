package Map;

public class HashMap {
    private String url;      // URL complète
    private Mapping mapping; // Mapping de la classe et de ses méthodes

    public HashMap() {}

    public HashMap(String url, Mapping mapping) {
        this.url = url;
        this.mapping = mapping;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Mapping getMapping() {
        return mapping;
    }

    public void setMapping(Mapping mapping) {
        this.mapping = mapping;
    }

    // Retourne le nom réel de la méthode correspondant à cette URL
    public String leMethode() {
        if (mapping != null) {
            String methodName = mapping.getMethodByUrl(url);
            if (methodName != null) {
                return methodName;
            }
        }
        return null; // aucune méthode liée à cette URL
    }

    // Vérifie si l'URL est bien associée à une méthode
    public boolean isAssociated() {
        return leMethode() != null;
    }
}
