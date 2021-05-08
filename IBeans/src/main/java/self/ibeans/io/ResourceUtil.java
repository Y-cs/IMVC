package self.ibeans.io;

import java.io.InputStream;

/**
 * @author Y-cs
 * @date 2021/4/19 14:14
 */
public class ResourceUtil {

    public static InputStream readResourceToInputStream(String path){
        return ResourceUtil.class.getClassLoader().getResourceAsStream(path);
    }


}
