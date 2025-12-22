package com.frieren.p17;

import jakarta.servlet.http.HttpSession;
import java.util.Enumeration;

/**
 * Une classe wrapper pour simplifier la manipulation de la HttpSession.
 */
public class MySession {
    private final HttpSession session;

    public MySession(HttpSession session) {
        this.session = session;
    }

    /**
     * Ajoute ou met à jour un attribut dans la session.
     * @param key Le nom de l'attribut.
     * @param value La valeur de l'attribut.
     */
    public void set(String key, Object value) {
        session.setAttribute(key, value);
    }

    /**
     * Récupère un attribut de la session.
     * @param key Le nom de l'attribut.
     * @return La valeur de l'attribut, ou null s'il n'existe pas.
     */
    public Object get(String key) {
        return session.getAttribute(key);
    }

    /**
     * Supprime un attribut de la session.
     * @param key Le nom de l'attribut à supprimer.
     */
    public void remove(String key) {
        session.removeAttribute(key);
    }

    /**
     * Invalide la session et supprime tous les attributs liés.
     */
    public void invalidate() {
        session.invalidate();
    }
    
    /**
     * Retourne une énumération de tous les noms d'attributs dans la session.
     * @return une Enumeration<String> des noms d'attributs.
     */
    public Enumeration<String> getAttributeNames() {
        return session.getAttributeNames();
    }
}
