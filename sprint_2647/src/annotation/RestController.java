package annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Une annotation de convenance qui combine @Controller et @ResponseBody.
 * Indique que toutes les méthodes de la classe retournent par défaut
 * des données dans le corps de la réponse.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RestController {
    String value() default "";
}
