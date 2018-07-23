package jworld;

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


/**
 *
 *
 * @author <a href="mailto:slemaguer@coli.uni-saarland.de">Sébastien Le Maguer</a>
 */
public class JWorldWrapper
{

    private static final double MAX_16_BIT = Short.MAX_VALUE;     // 32,767

    static {
        try {
            NativeUtils.loadLibraryFromJar("/libworld.so");
        } catch (IOException e) {
            e.printStackTrace(); // This is probably not the best way to handle exception :-)
        }
    }

    // Swig wrappers
    private SWIGTYPE_p_double x;
    private int x_length;


    private SWIGTYPE_p_double f0_cached;
    private int f0_length;

    private AudioInputStream input_stream;

    private SWIGTYPE_p_double time_axis;

    private int sample_rate;
    private double frame_period;

    public JWorldWrapper(int sample_rate, double frame_period)
    {
        input_stream = null;
        setFramePeriod(frame_period);
        setSampleRate(sample_rate);
        x = null;
    }
    public JWorldWrapper(AudioInputStream ais) throws Exception
    {
        input_stream = ais;
        this.fromAISToDoubleArray();
        setFramePeriod(5.0);
        setSampleRate((int) ais.getFormat().getSampleRate());
    }


    public double getFramePeriod() {
        return frame_period;
    }

    public void setFramePeriod(double frame_period) {
        this.frame_period = frame_period;
    }

    public int getSampleRate() {
        return sample_rate;
    }

    public void setSampleRate(int sample_rate) {
        this.sample_rate = sample_rate;
    }


    private void fromAISToDoubleArray() throws Exception {

        // Load data
        int bytesToRead = this.input_stream.available();
        byte[] data = new byte[bytesToRead];
        int bytesRead = this.input_stream.read(data);
        if (bytesToRead != bytesRead)
            throw new IllegalStateException("read only " + bytesRead + " of " + bytesToRead + " bytes");

        // Convert to swig double array
        int n = data.length;
        this.x_length = n/2;
        this.x = World.new_double_array(this.x_length);
        for (int i = 0; i < this.x_length; i++) {
            double v = ((short) (((data[2*i+1] & 0xFF) << 8) + (data[2*i] & 0xFF))) / ((double) MAX_16_BIT);
            World.double_array_setitem(x, i, v);
        }
    }

    public double[] extractF0(boolean cached) {
        int f0_length = World.GetSamplesForDIO(getSampleRate(), x_length, getFramePeriod());
        System.out.println(f0_length);
        SWIGTYPE_p_double tmp_f0 = World.new_double_array(f0_length);
        time_axis = World.new_double_array(f0_length);

        // Set options
        DioOption opt = new DioOption();
        World.InitializeDioOption(opt);
        opt.setSpeed(1);
        opt.setFrame_period(getFramePeriod());
        opt.setF0_floor(71.0);
        opt.setAllowed_range(0.1);

        // Extract F0
        World.Dio(x, x_length,
                  getSampleRate(),
                  opt,
                  this.time_axis,
                  tmp_f0);


        // Refining estimation
        f0_cached = World.new_double_array(f0_length);
        World.StoneMask(x, x_length,
                        getSampleRate(),
                        time_axis,
                        tmp_f0,
                        f0_length,
                        f0_cached);
        World.delete_double_array(tmp_f0);

        // Generate the proper array
        double[] f0 = new double[f0_length];
        for (int i=0; i<f0_length; i++) {
            f0[i] = World.double_array_getitem(f0_cached, i);
        }

        if (! cached) {
            World.delete_double_array(f0_cached);
        }
        return f0;
    }

    public double[][] extractSP() {
        return null;
    }

    public double[][] extractAP(double[] x) {
        return null;
    }


    public AudioInputStream synthesis(double[] f0, double[][] sp, double[][] ap) {
        int fft_len = sp[0].length - 1;

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
            for (int i=0; i<ap[t].length; i++) {
                World.double_array_setitem(row, i, ap[t][i]);
            }

            World.double_p_array_setitem(ap_s, t, row);
        }

        // Synthesis
        int y_length =  (int)((f0.length - 1) * frame_period / 1000.0 * sample_rate) + 1;
        SWIGTYPE_p_double y_s = World.new_double_array(y_length);
        World.Synthesis(f0_s, f0.length,
                        sp_s, ap_s,
                        fft_len, frame_period, sample_rate,
                        y_length, y_s);

        // To double array
        double[] y = new double[y_length];
        for (int i=0; i<y_length; i++) {
            y[i] = World.double_array_getitem(y_s, i);
        }

        // Generate audio inputstream
        AudioFormat format = new AudioFormat(sample_rate, 16, 1, true, false);   // use 16-bit audio, mono, signed PCM, little Endian
        byte[] data = new byte[2 * y.length];
        for (int i = 0; i < y.length; i++) {
            int temp = (short) (y[i] * MAX_16_BIT);
            data[2*i + 0] = (byte) temp;
            data[2*i + 1] = (byte) (temp >> 8);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        AudioInputStream ais = new AudioInputStream(bais, format, y.length);

        return ais;
    }
}
