package war.ui;

/** Separate launcher avoids the Java launcher special case for Application subclasses. */
public final class DesktopLauncher {
    private DesktopLauncher() {}
    public static void main(String[] args) { javafx.application.Application.launch(WarApplication.class, args); }
}
