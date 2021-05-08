package self.mvc.annotations;

import java.lang.annotation.*;

/**
 * @author Y-cs
 * @date 2021/5/8 18:04
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Security {

    String[] value();

}
