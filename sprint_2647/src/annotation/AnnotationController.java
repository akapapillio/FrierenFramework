package annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour lier un paramètre de méthode à un paramètre de requête HTTP.
 * Permet de spécifier le nom du paramètre de la requête à utiliser.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {
    /**
     * Le nom du paramètre de la requête HTTP.
     */
    String value();
}
