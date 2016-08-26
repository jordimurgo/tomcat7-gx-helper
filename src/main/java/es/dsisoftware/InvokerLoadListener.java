package es.dsisoftware;


import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import javax.servlet.*;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServlet;

import org.apache.catalina.ContainerServlet;

/**
 * http://snippets.dzone.com/posts/show/4831
 *
 * @author ricky
 */
@WebListener
public class InvokerLoadListener implements ServletContextListener {

    Logger LOG = Logger.getLogger(this.getClass().getCanonicalName());

    /**
     * Invoker parameter to setup the mapping name. By default is "/servlet/"
     */
    public static final String INVOKER_PREFIX_PARAMETER = "invoker.prefix";

    /**
     * Scans all classes accessible from the context class loader which
     * belong to the given package and subpackages.
     *
     * @return The list of classes found
     */
    private Set<Class> getClasses() {
        Set<Class> classes = new HashSet<Class>();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources("");
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if (resource.getProtocol().equals("jar")) {
                    // inside a jar => read the jar files and check
                    findClassesJar(resource, classes);
                } else if (resource.getProtocol().equals("file")) {
                    // read subdirectories and find
                    findClassesFile(new File(resource.getFile()), classes);
                } else {
                    System.err.println("Unknown protocol connection: " + resource);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    /**
     * Reads a jar file and checks all the classes inside it with the package
     * name specified.
     *
     * @param resource The resource url
     * @param classes
     * @return
     */
    private Set<Class> findClassesJar(URL resource, Set<Class> classes) {
        JarURLConnection jarConn = null;
        JarFile jar = null;
        try {
            jarConn = (JarURLConnection) resource.openConnection();
            jar = jarConn.getJarFile();
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();
                if (entry.getName().endsWith(".class")) {

                    // System.out.println(this.getClass().getCanonicalName() + ": jarclass: " + entry.getName() );

                    String name = entry.getName(); // .replace('/', '.');
                    name = name.substring(0, name.length() - 6);
                    checkClass(name, classes);
                } else {
                   // System.out.println(this.getClass().getCanonicalName() + ": unk : " + entry.getName() );
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                jar.close();
            } catch (IOException e) {
            }
        }
        return classes;
    }

    /**
     * Recursive method used to find all classes in a given file (file
     * or directory).
     *
     * @param file        The base directory
     * @return The same classes
     * @ classes The list of classes
     */
    private Set<Class> findClassesFile(File file, Set<Class> classes) {
        if (file.isFile() && file.getName().endsWith(".class")) {
            // System.out.println(this.getClass().getCanonicalName() + ": fileclass: " + file.getAbsolutePath() );
            //classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            checkClass(file.getName().substring(0, file.getName().length() - 6), classes);
        } else {
            if(file.isDirectory() && file.getName().equals("classes")) {
                File[] files = file.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return (pathname.getName().endsWith(".class"));
                    }
                });
                for (File f : files) {
                    findClassesFile(f, classes);
                }
            }

            // System.out.println(this.getClass().getCanonicalName() + ": fileunk: " + file.getAbsolutePath() );
        }
        return classes;
    }

    private Set<Class> checkClass(String name, Set<Class> classes) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class clazz = classLoader.loadClass(name);
            // System.out.println(this.getClass().getCanonicalName() + ": casting: " + clazz );

            // Class clazz = Class.forName(name);
            if (HttpServlet.class.isAssignableFrom(clazz)) {
                classes.add(clazz);
                // System.out.println(this.getClass().getCanonicalName() + ": found: " + clazz.getCanonicalName() );
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return classes;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext sc = sce.getServletContext();
        String prefix = sc.getInitParameter(INVOKER_PREFIX_PARAMETER);
        if (prefix == null) {
            prefix = "/servlet/";
        }

        // System.out.println(this.getClass().getCanonicalName() + ": Checking root package");
        // load classes under servlet.invoker
        Set<Class> classes = getClasses();
        if(classes.size()>0)
            System.out.println(this.getClass().getCanonicalName() + ": Fonund " + classes.size() + " Servlets");

        int i=0;
        for (Class clazz : classes) {
            String mapping = prefix + clazz.getName();
            ServletRegistration.Dynamic sr = sc.addServlet(clazz.getName(), clazz);
            if(sr != null) {
                // System.out.println(this.getClass().getCanonicalName() + ": Adding '" + clazz.getName() + "' in mapping '" + mapping + "'");
                i++;
                sr.addMapping(mapping);
            } else {
                // System.out.println(this.getClass().getCanonicalName() + ": Already registered servlet '" + clazz.getName() + "'");
            }
        }
        if(i!=0)
            System.out.println(this.getClass().getCanonicalName() + ": Autoregistered " + i + " Servlets");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
