package com.frieren.p17;

import java.util.HashMap;
import java.util.Map;

public class ModelView {

    private String view; // Nom de la JSP ou page à afficher
    private Map<String, Object> data; // Les données à passer à la vue

    public ModelView() {
        data = new HashMap<>();
    }

    public ModelView(String view) {
        this();
        this.view = view;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void addItem(String key, Object value) {
        data.put(key, value);
    }
}
