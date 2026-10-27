package war.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PackageProbeTest {
    @TempDir Path temp;
    @Test void headlessPackageEntryPointVerifiesResourcesHistoryAndExports() throws Exception {
        Path report = temp.resolve("package report.txt");
        DesktopLauncher.main(new String[]{"--smoke-test", "--report", report.toString()});
        assertTrue(Files.readString(report).startsWith("War packaged runtime: OK\njava.home="));
    }
    @Test void incompleteHealthCheckArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DesktopLauncher.main(new String[]{"--smoke-test"}));
    }
}
