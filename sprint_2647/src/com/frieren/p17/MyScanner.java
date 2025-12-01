package com.frieren.p17;

import annotation.Component;
import annotation.Controller;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class MyScanner {
    private final List<Class<?>> controllers = new ArrayList<>();
    private final List<Class<?>> components = new ArrayList<>();

    /**
     * Scanne un package pour trouver les classes annotées (@Controller, @Component, etc.).
     * @param packageName Le nom du package à scanner.
     */
    public void scanPackage(String packageName) throws Exception {
        System.out.println("🔍 Scan du package: " + packageName);
        
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(path);
        
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if ("file".equals(resource.getProtocol())) {
                scanDirectory(new File(resource.getFile()), packageName);
            }
        }
    }
    
    private void scanDirectory(File directory, String packageName) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(Controller.class)) {
                        controllers.add(clazz);
                        System.out.println("✅ Contrôleur trouvé: " + className);
                    }
                    if (clazz.isAnnotationPresent(Component.class)) {
                        components.add(clazz);
                        System.out.println("✅ Composant trouvé: " + className);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // Ignorer les classes qui ne peuvent être chargées
                }
            }
        }
    }
    
    public List<Class<?>> getControllers() {
        return controllers;
    }

    public List<Class<?>> getComponents() {
        return components;
    }
}
