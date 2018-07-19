// Testing
import org.testng.Assert;
import org.testng.annotations.*;
import java.io.IOException;
import cz.adamh.utils.NativeUtils;

// Example interface
import jworld.World;

public class JWorldTest {

    static {
        try {
            NativeUtils.loadLibraryFromJar("/libworld.so");
        } catch (IOException e) {
            e.printStackTrace(); // This is probably not the best way to handle exception :-)
        }
    }

    @Test
    public void testSynthesis() {
        World.Synthesis(null, 0,
                        null,
                        null,
                        512, 4, 5, 0,
                        null);
    }
}
