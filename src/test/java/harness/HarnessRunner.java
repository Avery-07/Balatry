package harness;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs every hand-rolled test harness (a class named {@code *Tests} with a {@code public static void main}) by
 * discovery, so adding a harness needs no registration. Each harness is launched in its own JVM: that way a
 * harness's {@code System.exit(non-zero)} on failure is simply the subprocess's exit status — nothing to
 * intercept, and no shared static state leaks between harnesses. The runner exits non-zero if any harness did,
 * which is what turns a red harness into a failed {@code mvn test}. This bridges the project's existing
 * convention to Maven without rewriting 22 files into JUnit.
 *
 * <p>Classpath and the JVM binary are inherited from this process, so the exec plugin needs no extra wiring:
 * it runs this one class, and this class fans out to the rest.
 */
public final class HarnessRunner {

    public static void main(String[] args) throws Exception {
        List<String> harnesses = discover();
        Collections.sort(harnesses);
        if (harnesses.isEmpty()) {
            System.err.println("HarnessRunner found no *Tests classes on the classpath");
            System.exit(1);
        }

        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Path testClasses = classesDir();                       // .../target/test-classes
        Path mainClasses = testClasses.resolveSibling("classes");   // .../target/classes
        // Build the child classpath explicitly: under the exec plugin, this process's own java.class.path is
        // the plugin's classpath, not the project's, so an inherited-only classpath cannot find the harnesses.
        String inherited = System.getProperty("java.class.path");
        String classpath = testClasses + File.pathSeparator + mainClasses
                + (inherited == null || inherited.isBlank() ? "" : File.pathSeparator + inherited);

        List<String> failed = new ArrayList<>();
        for (String name : harnesses) {
            System.out.println("\n=== " + name + " ===");
            int code = runInSubprocess(java, classpath, name);
            if (code != 0) failed.add(name);
        }

        System.out.println("\n================ HARNESS SUMMARY ================");
        System.out.println(harnesses.size() + " harnesses, " + failed.size() + " failed");
        for (String f : failed) System.out.println("  FAILED: " + f);
        System.exit(failed.isEmpty() ? 0 : 1);
    }

    /** Launches one harness main in a fresh JVM, streaming its output through; returns its exit code. */
    private static int runInSubprocess(String java, String classpath, String className) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(java, "-cp", classpath, className)
                .redirectErrorStream(true)
                .inheritIO()
                .start();
        return p.waitFor();
    }

    /** Scans the test-classes output directory for every {@code *Tests.class} under {@code model/}. */
    private static List<String> discover() throws IOException, URISyntaxException {
        Path root = classesDir();
        Path modelRoot = root.resolve("model");
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(modelRoot)) return names;
        try (var stream = Files.walk(modelRoot)) {
            stream.filter(pth -> pth.toString().endsWith("Tests.class")).forEach(pth -> {
                String rel = root.relativize(pth).toString().replace(File.separatorChar, '.');
                names.add(rel.substring(0, rel.length() - ".class".length()));
            });
        }
        return names;
    }

    /**
     * The directory this class was loaded from (target/test-classes). Resolving the class's own resource URL
     * through {@link java.net.URI} and {@link Path} handles platform path forms correctly — notably Windows,
     * where a URL path reads {@code /C:/...} and cannot be fed to {@link Path#of(String)} directly.
     */
    private static Path classesDir() throws URISyntaxException {
        var url = HarnessRunner.class.getResource("/harness/HarnessRunner.class");
        if (url == null) throw new IllegalStateException("cannot locate own class file");
        Path self = Path.of(url.toURI());                 // .../test-classes/harness/HarnessRunner.class
        return self.getParent().getParent();              // -> .../test-classes
    }
}
