package war.ui;

/** Separate launcher avoids the Java launcher special case for Application subclasses. */
public final class DesktopLauncher {
    private DesktopLauncher() {}
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--version")) {
            System.out.println("War " + java.util.Objects.toString(DesktopLauncher.class.getPackage().getImplementationVersion(), "development"));
            return;
        }
        if (args.length > 0 && args[0].equals("--smoke-test")) {
            if (args.length != 3 || !args[1].equals("--report")) throw new IllegalArgumentException("Use --smoke-test --report <path>");
            try {
                PackageProbe.verify();
                java.nio.file.Files.writeString(java.nio.file.Path.of(args[2]),
                        "War packaged runtime: OK\njava.home=" + System.getProperty("java.home") + "\n");
                return;
            } catch (Exception e) { throw new IllegalStateException("War package check failed", e); }
        }
        javafx.application.Application.launch(WarApplication.class, args);
    }
}
