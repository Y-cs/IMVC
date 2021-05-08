package self.ibeans.reflex;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.InvalidPathException;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author Y-cs
 * @date 2021/4/21 13:23
 */
public class PackageUtil {

    private static final byte[] JAR_MAGIC = {'P', 'K', 3, 4};

    private PackageUtil() {
    }

    static class PackageUtilInstance {
        public static PackageUtil packageUtil = new PackageUtil();
    }

    public static PackageUtil getInstance() {
        return PackageUtilInstance.packageUtil;
    }

    public Set<Class<?>> getClassFromPackage(String packageName, Function<Class<?>, Boolean> testFuc) throws IOException, ClassNotFoundException {
        String packagePath = this.getPackagePath(packageName);
        List<String> clazzPath = getAllPathFromPkg(packagePath);
        return getClassIfMatching(testFuc, clazzPath);
    }

    private List<String> getAllPathFromPkg(String packagePath) throws IOException {
        List<String> names = new ArrayList<>();
        for (URL url : this.getResources(packagePath)) {
            names.addAll(list(url, packagePath));
        }
        return names;
    }

    private Set<Class<?>> getClassIfMatching(Function<Class<?>, Boolean> testFuc, List<String> names) throws ClassNotFoundException {
        Set<Class<?>> result = new HashSet<>();
        for (String child : names) {
            if (child.endsWith(".class")) {
                String externalName = child.substring(0, child.indexOf('.')).replace('/', '.');
                ClassLoader loader = getClassLoader();
                if (externalName.contains("servlet")) {
                    continue;
                }
                Class<?> type = loader.loadClass(externalName);
                if (testFuc != null) {
                    if (testFuc.apply(type)) {
                        result.add(type);
                    }
                } else {
                    result.add(type);
                }
            }
        }
        return result;
    }

    protected List<URL> getResources(String path) throws IOException {
        return Collections.list(Thread.currentThread().getContextClassLoader().getResources(path));
    }

    public String getPackagePath(String packageName) {
        return packageName == null ? null : packageName.replace('.', '/');
    }

    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public List<String> list(URL url, String path) throws IOException {
        InputStream is = null;
        try {
            List<String> resources = new ArrayList<>();

            // First, try to find the URL of a JAR file containing the requested resource. If a JAR
            // file is found, then we'll list child resources by reading the JAR.
            URL jarUrl = findJarForResource(url);
            if (jarUrl != null) {
                is = jarUrl.openStream();

                resources = listResources(new JarInputStream(is), path);
            } else {
                List<String> children = new ArrayList<>();
                try {
                    if (isJar(url)) {
                        // Some versions of JBoss VFS might give a JAR stream even if the resource
                        // referenced by the URL isn't actually a JAR
                        is = url.openStream();
                        try (JarInputStream jarInput = new JarInputStream(is)) {

                            for (JarEntry entry; (entry = jarInput.getNextJarEntry()) != null; ) {

                                children.add(entry.getName());
                            }
                        }
                    } else {
                        /*
                         * Some servlet containers allow reading from directory resources like a
                         * text file, listing the child resources one per line. However, there is no
                         * way to differentiate between directory and file resources just by reading
                         * them. To work around that, as each line is read, try to look it up via
                         * the class loader as a child of the current resource. If any line fails
                         * then we assume the current resource is not a directory.
                         */
                        is = url.openStream();
                        List<String> lines = new ArrayList<>();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                            for (String line; (line = reader.readLine()) != null; ) {

                                lines.add(line);
                                if (getResources(path + "/" + line).isEmpty()) {
                                    lines.clear();
                                    break;
                                }
                            }
                        } catch (InvalidPathException e) {
                            // #1974
                            lines.clear();
                        }
                        if (!lines.isEmpty()) {

                            children.addAll(lines);
                        }
                    }
                } catch (FileNotFoundException e) {
                    /*
                     * For file URLs the openStream() call might fail, depending on the servlet
                     * container, because directories can't be opened for reading. If that happens,
                     * then list the directory directly instead.
                     */
                    if ("file".equals(url.getProtocol())) {
                        File file = new File(url.getFile());

                        if (file.isDirectory()) {

                            children = Arrays.asList(file.list());
                        }
                    } else {
                        // No idea where the exception came from so rethrow it
                        throw e;
                    }
                }

                // The URL prefix to use when recursively listing child resources
                String prefix = url.toExternalForm();
                if (!prefix.endsWith("/")) {
                    prefix = prefix + "/";
                }

                // Iterate over immediate children, adding files and recursing into directories
                for (String child : children) {
                    String resourcePath = path + "/" + child;
                    resources.add(resourcePath);
                    URL childUrl = new URL(prefix + child);
                    resources.addAll(list(childUrl, resourcePath));
                }
            }

            return resources;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    protected URL findJarForResource(URL url) throws MalformedURLException {

        boolean continueLoop = true;
        while (continueLoop) {
            try {
                url = new URL(url.getFile());

            } catch (MalformedURLException e) {
                // This will happen at some point and serves as a break in the loop
                continueLoop = false;
            }
        }

        // Look for the .jar extension and chop off everything after that
        StringBuilder jarUrl = new StringBuilder(url.toExternalForm());
        int index = jarUrl.lastIndexOf(".jar");
        if (index >= 0) {
            jarUrl.setLength(index + 4);

        } else {

            return null;
        }

        // Try to open and test it
        try {
            URL testUrl = new URL(jarUrl.toString());
            if (isJar(testUrl)) {
                return testUrl;
            } else {

                jarUrl.replace(0, jarUrl.length(), testUrl.getFile());
                File file = new File(jarUrl.toString());

                // File name might be URL-encoded
                if (!file.exists()) {
                    try {
                        file = new File(URLEncoder.encode(jarUrl.toString(), "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("Unsupported encoding?  UTF-8?  That's unpossible.");
                    }
                }

                if (file.exists()) {

                    testUrl = file.toURI().toURL();
                    if (isJar(testUrl)) {
                        return testUrl;
                    }
                }
            }
        } catch (MalformedURLException e) {
            System.out.println("Invalid JAR URL: " + jarUrl);
        }
        return null;
    }

    protected boolean isJar(URL url) {
        return isJar(url, new byte[JAR_MAGIC.length]);
    }

    protected boolean isJar(URL url, byte[] buffer) {
        try (InputStream is = url.openStream()) {
            is.read(buffer, 0, JAR_MAGIC.length);
            if (Arrays.equals(buffer, JAR_MAGIC)) {
                return true;
            }
        } catch (Exception e) {
            // Failure to read the stream means this is not a JAR
        }
        return false;
    }

    protected List<String> listResources(JarInputStream jar, String path) throws IOException {
        // Include the leading and trailing slash when matching names
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }

        // Iterate over the entries and collect those that begin with the requested path
        List<String> resources = new ArrayList<>();
        for (JarEntry entry; (entry = jar.getNextJarEntry()) != null; ) {
            if (!entry.isDirectory()) {
                // Add leading slash if it's missing
                StringBuilder name = new StringBuilder(entry.getName());
                if (name.charAt(0) != '/') {
                    name.insert(0, '/');
                }

                // Check file name
                if (name.indexOf(path) == 0) {
                    // Trim leading slash
                    resources.add(name.substring(1));
                }
            }
        }
        return resources;
    }

}
