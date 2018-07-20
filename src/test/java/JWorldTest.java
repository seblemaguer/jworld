// Testing
import org.testng.Assert;
import org.testng.annotations.*;
import cz.adamh.utils.NativeUtils;

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

    static {
        try {
            NativeUtils.loadLibraryFromJar("/libworld.so");
        } catch (IOException e) {
            e.printStackTrace(); // This is probably not the best way to handle exception :-)
        }
    }


    /**
     * Reads audio samples from a file (in .wav or .au format) and returns
     * them as a double array with values between -1.0 and +1.0.
     *
     * @param  filename the name of the audio file
     * @return the array of samples
     */
    public static double[] load(String filename) {
        byte[] data = readByte(filename);
        int n = data.length;
        double[] d = new double[n/2];
        for (int i = 0; i < n/2; i++) {
            d[i] = ((short) (((data[2*i+1] & 0xFF) << 8) + (data[2*i] & 0xFF))) / ((double) MAX_16_BIT);
        }
        return d;
    }

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
    public static void save(String filename, double[] samples, int sample_rate) {
        if (samples == null) {
            throw new IllegalArgumentException("samples[] is null");
        }

        // assumes 44,100 samples per second
        // use 16-bit audio, mono, signed PCM, little Endian
        AudioFormat format = new AudioFormat(sample_rate, 16, 1, true, false);
        byte[] data = new byte[2 * samples.length];
        for (int i = 0; i < samples.length; i++) {
            int temp = (short) (samples[i] * MAX_16_BIT);
            data[2*i + 0] = (byte) temp;
            data[2*i + 1] = (byte) (temp >> 8);
        }

        // now save the file
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            AudioInputStream ais = new AudioInputStream(bais, format, samples.length);
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
        int fft_len = 1024;

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
        int fs;
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
            fs = byteBuffer.getInt();

            // Get the frame period
            frame_period = byteBuffer.getDouble();

            // From bytes to double array :)
            DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
            sp = new double[f0.length][doubleBuffer.remaining()/f0.length];
            System.out.println(sp[0].length);
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
            System.out.println(ap[0][0]);
        } catch (IOException ex) {
            throw ex;
        }

        // Generate F0 swig
        SWIGTYPE_p_double f0_s = World.new_double_array(f0.length);
        for (int i=0; i<f0.length; i++) {
            World.double_array_setitem(f0_s, i, f0[i]);
        }

        // Generate SP swig
        SWIGTYPE_p_p_double sp_s = World.new_double_p_array(sp.length);
        for (int t=0; t<sp.length; t++) {
            SWIGTYPE_p_double row = World.new_double_array(sp[0].length);
            for (int i=0; i<sp[t].length; i++)
                World.double_array_setitem(row, i, sp[t][i]);

            World.double_p_array_setitem(sp_s, t, row);
        }


        // Generate AP swig
        SWIGTYPE_p_p_double ap_s = World.new_double_p_array(ap.length);
        for (int t=0; t<ap.length; t++) {
            SWIGTYPE_p_double row = World.new_double_array(ap[0].length);
            for (int i=0; i<ap[t].length; i++)
                World.double_array_setitem(row, i, ap[t][i]);

            World.double_p_array_setitem(ap_s, t, row);
        }


        int y_length =  (int)((f0.length - 1) * frame_period / 1000.0 * fs) + 1;
        SWIGTYPE_p_double y_s = World.new_double_array(y_length);
        World.Synthesis(f0_s, f0.length,
                        sp_s, ap_s,
                        fft_len, frame_period, fs,
                        y_length, y_s);

        double[] y = new double[y_length];
        for (int i=0; i<y_length; i++) {
            y[i] = World.double_array_getitem(y_s, i);
        }

        // Saving as a check part
        JWorldTest.save("/home/slemaguer/tata.wav", y, fs);

        // Load reference
        double[] y_ref = load("/home/slemaguer/test_reb.wav");

        // Assert equality
        Assert.assertTrue(Arrays.equals(y_ref, y));
    }
}
