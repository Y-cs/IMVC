package self.ibeans.reflex;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author Y-cs
 * @date 2021/4/19 17:11
 */
public class ReflexUtil {

    public static <T> T getObject(Class<T> cls) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor constructor = cls.getConstructor();
        return (T) constructor.newInstance();
    }


}
