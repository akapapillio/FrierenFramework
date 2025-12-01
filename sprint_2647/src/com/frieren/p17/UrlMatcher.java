package com.frieren.p17;

import java.util.HashMap;
import java.util.Map;

public class UrlMatcher {
    
    /**
     * Vérifie si une URL correspond à un pattern (modèle) donné.
     * @param pattern Le pattern avec des variables (ex: /users/{id})
     * @param url L'URL de la requête (ex: /users/123)
     * @return true si l'URL correspond au pattern, false sinon.
     */
    public static boolean matches(String pattern, String url) {
        String[] patternParts = pattern.split("/");
        String[] urlParts = url.split("/");
        
        if (patternParts.length != urlParts.length) {
            return false;
        }
        
        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                // C'est une variable, on accepte n'importe quelle valeur.
                continue;
            }
            if (!patternParts[i].equals(urlParts[i])) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Extrait les variables de l'URL en se basant sur le pattern.
     * @param pattern Le pattern avec des variables (ex: /users/{id})
     * @param url L'URL de la requête (ex: /users/123)
     * @return Une Map contenant les noms des variables et leurs valeurs (ex: {"id": "123"}).
     */
    public static Map<String, String> extractParameters(String pattern, String url) {
        Map<String, String> params = new HashMap<>();
        String[] patternParts = pattern.split("/");
        String[] urlParts = url.split("/");
        
        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                params.put(paramName, urlParts[i]);
            }
        }
        return params;
    }
}
