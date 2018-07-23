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

import java.net.URL;

import com.google.common.io.ByteStreams;

// Example interface
import jworld.*;

public class JWorldTest {

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
    public void extractF0() throws Exception {
        URL url = JWorldTest.class.getResource("/vaiueo2d.wav");
        AudioInputStream ais = AudioSystem.getAudioInputStream(url);

        JWorldWrapper jww = new JWorldWrapper(ais);
        double[] f0 = jww.extractF0(false);

        // Load reference F0
        byte[] bytes = ByteStreams.toByteArray(JWorldTest.class.getResourceAsStream("/test.f0"));
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes);
        byteBuffer.rewind();

        double[] f0_ref = new double[byteBuffer.asDoubleBuffer().remaining()];
        byteBuffer.asDoubleBuffer().get(f0_ref);

        // for(int i=0;i<f0_ref.length;i++){
        //     System.out.println("test f0: " + f0[i] + " =? " + f0_ref[i]);
        // }

        // Assert !
        Assert.assertEquals(f0.length, f0_ref.length);
        for (int t=0; t<f0.length; t++)
            Assert.assertEquals(f0[t], f0_ref[t], 0.00001);
    }


    @Test
    public void extractSP() throws Exception {
        URL url = JWorldTest.class.getResource("/vaiueo2d.wav");
        AudioInputStream ais = AudioSystem.getAudioInputStream(url);

        // Extract spectrum
        JWorldWrapper jww = new JWorldWrapper(ais);
        double[] f0 = jww.extractF0(true);
        double[][] sp = jww.extractSP();

        // Load spectrum bytes
        byte[] bytes = ByteStreams.toByteArray(JWorldTest.class.getResourceAsStream("/test.sp"));
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes);
        byteBuffer.rewind();

        // Ignore spectrum header
        byteBuffer.getInt(); byteBuffer.getDouble();
        DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();

        // Load actual reference spectrum
        double[][] sp_ref = new double[f0.length][doubleBuffer.remaining()/f0.length];
        for(int t=0; t<f0.length; t++){
            doubleBuffer.get(sp_ref[t]);
        }

        // Assert !
        Assert.assertEquals(sp_ref.length, sp.length);
        Assert.assertEquals(sp_ref[0].length, sp[0].length);
        for (int t=0; t<f0.length; t++) {
            for (int i=0; i<sp[t].length; i++) {
                // System.out.println("test sp: " + sp_ref[t][i] + " =? " + sp[t][i]);
                Assert.assertEquals(sp_ref[t][i], sp[t][i], 0.001); // FIXME: check problem for rounding doubles!
            }
        }
    }


    @Test
    public void extractAP() throws Exception {
        URL url = JWorldTest.class.getResource("/vaiueo2d.wav");
        AudioInputStream ais = AudioSystem.getAudioInputStream(url);

        // Extract aperiodicity
        JWorldWrapper jww = new JWorldWrapper(ais);
        double[] f0 = jww.extractF0(true);
        double[][] ap = jww.extractAP();

        // Load aperiodicity bytes
        byte[] bytes = ByteStreams.toByteArray(JWorldTest.class.getResourceAsStream("/test.ap"));
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes);
        byteBuffer.rewind();

        // Load actual reference aperiodicity
        DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
        double[][] ap_ref = new double[f0.length][doubleBuffer.remaining()/f0.length];
        for(int t=0; t<f0.length; t++){
            doubleBuffer.get(ap_ref[t]);
        }

        // Assert !
        Assert.assertEquals(ap_ref.length, ap.length);
        Assert.assertEquals(ap_ref[0].length, ap[0].length);
        for (int t=0; t<f0.length; t++) {
            for (int i=0; i<ap[t].length; i++) {
                // System.out.println("test ap: " + ap_ref[t][i] + " =? " + ap[t][i]);
                Assert.assertEquals(ap_ref[t][i], ap[t][i], 0.001); // FIXME: check problem for rounding doubles!
            }
        }
    }

    // @Test
    public void testSynthesis() throws Exception {
        // FIXME: Load some resources (not added to the repo for space, they should be produced by analysis first!!)

        //  - F0

        // Load reference F0
        byte[] bytes = ByteStreams.toByteArray(JWorldTest.class.getResourceAsStream("/test.f0"));
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes);
        byteBuffer.rewind();

        double[] f0 = new double[byteBuffer.asDoubleBuffer().remaining()];
        byteBuffer.asDoubleBuffer().get(f0);

        //  - SP

        // Load spectrum bytes
        bytes = ByteStreams.toByteArray(JWorldTest.class.getResourceAsStream("/test.sp"));
        byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes);
        byteBuffer.rewind();

        // Load spectrum header
        int sample_rate = byteBuffer.getInt();
        double frame_period = byteBuffer.getDouble();

        // Load actual reference spectrum
        DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
        double[][] sp = new double[f0.length][doubleBuffer.remaining()/f0.length];
        for(int t=0; t<f0.length; t++){
            doubleBuffer.get(sp[t]);
        }

        //  - AP
        // Load aperiodicity bytes
        bytes = ByteStreams.toByteArray(JWorldTest.class.getResourceAsStream("/test.ap"));
        byteBuffer = ByteBuffer.allocate(bytes.length);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.put(bytes);
        byteBuffer.rewind();

        // Load actual reference aperiodicity
        doubleBuffer = byteBuffer.asDoubleBuffer();
        double[][] ap = new double[f0.length][doubleBuffer.remaining()/f0.length];
        for(int t=0; t<f0.length; t++){
            doubleBuffer.get(ap[t]);
        }

        // Synthesize
        JWorldWrapper jww = new JWorldWrapper(sample_rate, frame_period);
        AudioInputStream ais = jww.synthesis(f0, sp, ap);

        // Load reference
        URL url = JWorldTest.class.getResource("/vaiueo2d_rec.wav");
        AudioInputStream ref_ais = AudioSystem.getAudioInputStream(url);

        // Assert equality
        Assert.assertEquals(ref_ais, ais);
    }
}
