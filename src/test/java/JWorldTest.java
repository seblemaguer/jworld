// Testing
import org.testng.Assert;
import org.testng.annotations.*;

// Audio
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

// IO
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.channels.FileChannel;

import java.util.Arrays;

// Example interface
import jworld.*;

public class JWorldTest {

    // return data as a byte array
    private static byte[] readByte(String filename) {
        byte[] data = null;
        AudioInputStream ais = null;
        try {

            // try to read from file
            File file = new File(filename);
            if (file.exists()) {
                ais = AudioSystem.getAudioInputStream(file);
                int bytesToRead = ais.available();
                data = new byte[bytesToRead];
                int bytesRead = ais.read(data);
                if (bytesToRead != bytesRead)
                    throw new IllegalStateException("read only " + bytesRead + " of " + bytesToRead + " bytes");
            }

            // // try to read from URL
            // else {
            //     URL url = StdAudio.class.getResource(filename);
            //     ais = AudioSystem.getAudioInputStream(url);
            //     int bytesToRead = ais.available();
            //     data = new byte[bytesToRead];
            //     int bytesRead = ais.read(data);
            //     if (bytesToRead != bytesRead)
            //         throw new IllegalStateException("read only " + bytesRead + " of " + bytesToRead + " bytes");
            // }
        }
        catch (IOException e) {
            throw new IllegalArgumentException("could not read '" + filename + "'", e);
        }

        catch (UnsupportedAudioFileException e) {
            throw new IllegalArgumentException("unsupported audio format: '" + filename + "'", e);
        }

        return data;
    }

    private static final double MAX_16_BIT = Short.MAX_VALUE;     // 32,767

    /**
     * Saves the double array as an audio file (using .wav or .au format).
     *
     * @param  filename the name of the audio file
     * @param  samples the array of samples
     * @throws IllegalArgumentException if unable to save {@code filename}
     * @throws IllegalArgumentException if {@code samples} is {@code null}
     */
    public static void save(String filename, AudioInputStream ais) {

        // now save the file
        try {
            if (filename.endsWith(".wav") || filename.endsWith(".WAV")) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(filename));
            }
            else if (filename.endsWith(".au") || filename.endsWith(".AU")) {
                AudioSystem.write(ais, AudioFileFormat.Type.AU, new File(filename));
            }
            else {
                throw new IllegalArgumentException("unsupported audio format: '" + filename + "'");
            }
        }
        catch (IOException ioe) {
            throw new IllegalArgumentException("unable to save file '" + filename + "'", ioe);
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
            ByteBuffer byteBuffer = ByteBuffer.allocate((int)fc.size());
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(byteBuffer);

            // Rewind to be at 0!
            byteBuffer.rewind();

            // From bytes to double array :)
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            f0 = new double[doubleBuffer.remaining()];
            doubleBuffer.get(f0);
        } catch (IOException ex) {
            throw ex;
        }

        //  - SP
        int sample_rate;
        double frame_period;

        double[][] sp;
        try(FileChannel fc = (FileChannel) Files.newByteChannel(Paths.get("/home/slemaguer/test.sp"),
                                                                StandardOpenOption.READ)) {
            // Loadfile
            ByteBuffer byteBuffer = ByteBuffer.allocate((int)fc.size());
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(byteBuffer);

            // Rewind to be at 0!
            byteBuffer.rewind();

            // Get the sample rate
            sample_rate = byteBuffer.getInt();

            // Get the frame period
            frame_period = byteBuffer.getDouble();

            // From bytes to double array :)
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            sp = new double[f0.length][doubleBuffer.remaining()/f0.length];
            for (int t=0; t<f0.length; t++) {
                doubleBuffer.get(sp[t]);
            }
        } catch (IOException ex) {
            throw ex;
        }

        //  - AP
        double[][] ap;
        try(FileChannel fc = (FileChannel) Files.newByteChannel(Paths.get("/home/slemaguer/test.ap"),
                                                                StandardOpenOption.READ)) {
            // Loadfile
            ByteBuffer byteBuffer = ByteBuffer.allocate((int)fc.size());
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            fc.read(byteBuffer);

            // Rewind to be at 0!
            byteBuffer.rewind();

            // From bytes to double array :)
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            ap = new double[f0.length][doubleBuffer.remaining()/f0.length];
            for (int t=0; t<f0.length; t++) {
                doubleBuffer.get(ap[t]);
            }
        } catch (IOException ex) {
            throw ex;
        }

        // Saving as a check part
        JWorldWrapper jww = new JWorldWrapper();
        AudioInputStream ais = jww.synthesis(f0, sp, ap, sample_rate, frame_period);
        JWorldTest.save("/home/slemaguer/tata.wav", ais);

        // Load reference
        File file = new File("/home/slemaguer/test_reb.wav");
        AudioInputStream ref_ais = AudioSystem.getAudioInputStream(file);

        // Assert equality
        Assert.assertEquals(ref_ais, ais);
    }
}
