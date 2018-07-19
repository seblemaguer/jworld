// Testing
import org.testng.Assert;
import org.testng.annotations.*;
import cz.adamh.utils.NativeUtils;

// IO
import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.channels.FileChannel;

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
    public void testSynthesis() throws Exception {
        // FIXME: Load some resources (not added to the repo for space, they should be produced by analysis first!!)

        //  - F0
        double[] f0;
        try(FileChannel fc = (FileChannel) Files.newByteChannel(Paths.get("/home/slemaguer/test.f0"),
                                                                StandardOpenOption.READ)) {

            // Loadfile
            System.out.println(fc.size());
            ByteBuffer byteBuffer = ByteBuffer.allocate((int)fc.size());
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(byteBuffer);

            // Rewind to be at 0!
            byteBuffer.rewind();

            // From bytes to double array :)
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            f0 = new double[doubleBuffer.remaining()];
            doubleBuffer.get(f0);

            // Checking
            for (int i=0; i<f0.length; i++)
                System.out.println(f0[i]);
        } catch (IOException ex) {
            throw ex;
        }

        //  - SP

        //  - AP
        // World.Synthesis(null, 0,
        //                 null,
        //                 null,
        //                 512, 4, 5, 0,
        //                 null);

        Assert.assertEquals(false, true);
    }
}
