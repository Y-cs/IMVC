package ibeans.test;

import org.junit.Test;
import self.ibeans.anno.ScanConfig;
import self.ibeans.reflex.PackageUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * @author Y-cs
 * @date 2021/4/19 14:05
 */
@ScanConfig
public class XmlParseTest {

    public static void main(String[] args) {

//        ApplicationContext applicationContext = new ClassApplicationContext();
//        applicationContext.start(XmlParseTest.class, args);

    }

    @Test
    public void testPackageUtil() throws IOException, ClassNotFoundException {
        Set<Class<?>> classFromPackage = PackageUtil.getInstance().getClassFromPackage("self.ibeans", null);

        System.out.println(classFromPackage);
    }

    @Test
    public void testReplace(){
        Class<? extends XmlParseTest> clazz = this.getClass();
        System.out.println(clazz.getSimpleName().replaceFirst(".", clazz.getSimpleName().substring(0, 1).toLowerCase(Locale.ROOT)));
    }


}


