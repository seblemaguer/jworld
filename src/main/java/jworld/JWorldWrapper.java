package jworld;

import java.io.ByteArrayInputStream;
import java.io.IOException;

// Audio
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import cz.adamh.utils.NativeUtils;


/**
 *  The wrapper class to be used to call the World vocoder.
 *
 *  This class is wrapping the 2 modes (analysis and synthesis). The constructor used to build the
 *  object is going to determine the mode in which the object should be used for.
 *
 *  For more information about the world vocoder, please have a look at the author website <a href="http://www.kki.yamanashi.ac.jp/~mmorise/world/english/">http://www.kki.yamanashi.ac.jp/~mmorise/world/english/</a>
 *
 *  @author <a href="mailto:slemaguer@coli.uni-saarland.de">Sébastien Le Maguer</a>
 */
public class JWorldWrapper
{
    /* Loading library part */
    static {
        String libResourceName;
        String osName = System.getProperty("os.name");
        switch (osName) {
            case ("Mac OS X"):
                libResourceName = "libworld.dylib";
                break;
            case ("Linux"):
                libResourceName = "libworld.so";
                break;
            default:
                throw new RuntimeException("Cannot load library for OS: " + osName);
        }
        try {
            NativeUtils.loadLibraryFromJar("/" + libResourceName);
        } catch (IOException e) {
            e.printStackTrace(); // This is probably not the best way to handle exception :-)
        }
    }

    // Signal swig wrapper
    private SWIGTYPE_p_double x; /*< The signal samples cached in swig format used in the analysis mode */
    private int x_length; /*< the number of (double) samples in the signal used in the analysis mode */

    // F0/time swig wrappers
    private SWIGTYPE_p_double f0_cached; /*< the F0 values cached in swig format used in the analysis mode */
    private SWIGTYPE_p_double time_axis; /*< the time axis cached in swig format used in the analysis mode */
    private int f0_length; /*< the number of frames (based on the number of f0 values) used in the analysis mode */

    // The signal input stream
    private AudioInputStream input_stream; /*< The audio input stream used in the analysis mode */

    // Some options
    private int sample_rate; /*< The sample rate of the signal */
    private double frame_period; /*< The frame period in ms (default value: 5.0ms) */
    private int speed; /*< The speed of the speech (default value: 1) */
    private double f0_floor; /*< The lower F0 value in Hz (default value: 71Hz) */
    private double f0_ceil; /*< The upper F0 value in Hz (default value: 800Hz) */
    private double f0_allowed_range; /*< The threshold to fix f0 contour (default value: 0.1) */
    private double q1; /*< The compensation lifter parameter used by cheapstick in the analysis mode (default value: -0.15) */


    /**
     *  The "synthesis mode" constructor.
     *
     *  @param sample_rate the sample rate of the produced wave
     *  @param frame_period the frame period used during the extraction stage
     */
    public JWorldWrapper(int sample_rate, double frame_period)
    {
        input_stream = null;
        setFramePeriod(frame_period);
        setSampleRate(sample_rate);
        x = null;
    }

    /**
     *  The "analysis mode" constructor
     *
     *  @param ais the audioinputstream filled with audio information
     */
    public JWorldWrapper(AudioInputStream ais) throws IOException
    {
        input_stream = ais;
        this.fromAISToDoubleArray();
        setFramePeriod(5.0);
        setQ1(-0.15);
        setF0Floor(71.0);
        setF0Ceil(800.0);
        setF0AllowedRange(0.1);
        setSpeed(1);
        setSampleRate((int) ais.getFormat().getSampleRate());
    }


    /*****************************************************************************************************
     ** Analysis entry part
     *****************************************************************************************************/

    /**
     *  Method to extract the F0 in alaysis mode
     *
     *  @param cached should the extraction be keeped in the cache? (needed if spectrum and/or aperiodicity should be extracted after!)
     *  @return the extracted f0
     *  @throws JWorldException if we are not in analysis mode !
     */
    public double[] extractF0(boolean cached) throws JWorldException{
        if (x == null)
            throw new JWorldException("Samples should be loaded, you should use the constructor which expect a AudioInputStream as parameter");

        // Compute length based on information
        f0_length = World.GetSamplesForDIO(getSampleRate(), x_length, getFramePeriod());

        // allocate needed memory
        SWIGTYPE_p_double tmp_f0 = World.new_double_array(f0_length);
        time_axis = World.new_double_array(f0_length);

        // Set options
        DioOption opt = new DioOption();
        World.InitializeDioOption(opt);
        opt.setSpeed(getSpeed());
        opt.setFrame_period(getFramePeriod());
        opt.setF0_floor(getF0Floor());
        opt.setAllowed_range(getF0AllowedRange());

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

        // Cleaning memory
        if (! cached) {
            World.delete_double_array(f0_cached);
            World.delete_double_array(time_axis);
        }

        return f0;
    }

    /**
     *  Method to extract the spectrum in alaysis mode
     *
     *  @return the extracted spectrum with the shape (nb_frames, fft_size/2+1)
     *  @throws JWorldException if the F0 has not been extracted and cached!
     */
    public double[][] extractSP() throws JWorldException{
        if (f0_cached == null)
            throw new JWorldException("To extract the spectrum, the F0 must be cached when extracted!");

        CheapTrickOption opt = new CheapTrickOption();
        World.InitializeCheapTrickOption(getSampleRate(), opt);

        opt.setQ1(getQ1());
        opt.setF0_floor(getF0Floor());

        // Compute FFT size
        int fft_size = World.GetFFTSizeForCheapTrick(getSampleRate(), opt);

        // Allocate memory
        SWIGTYPE_p_p_double spec_tmp = World.new_double_p_array(f0_length);
        for (int t=0; t<f0_length; t++)
            World.double_p_array_setitem(spec_tmp, t,
                                         World.new_double_array(fft_size/2+1));

        // Compute spectrogram
        World.CheapTrick(x, x_length,
                         sample_rate, time_axis,
                         f0_cached, f0_length,
                         opt, spec_tmp);

        // Free the memory while generating the java array
        double[][] spec = new double[f0_length][fft_size/2+1];
        for (int t=0; t<f0_length; t++) {
            SWIGTYPE_p_double tmp = World.double_p_array_getitem(spec_tmp, t);

            for (int i=0; i<spec[0].length; i++) {
                spec[t][i] = World.double_array_getitem(tmp, i);
            }

            World.delete_double_array(tmp);
        }
        World.delete_double_p_array(spec_tmp);

        return spec;
    }

    /**
     *  Method to extract the aperiodicity in alaysis mode
     *
     *  @return the extracted aperiodicity with the shape (nb_frames, fft_size/2+1)
     *  @throws JWorldException if the F0 has not been extracted and cached!
     */
    public double[][] extractAP() throws JWorldException{
        if (f0_cached == null)
            throw new JWorldException("To extract the aperiodicity, the F0 must be cached when extracted!");

        // Get FFT size
        CheapTrickOption ch_opt = new CheapTrickOption();
        World.InitializeCheapTrickOption(getSampleRate(), ch_opt);

        ch_opt.setQ1(getQ1());
        ch_opt.setF0_floor(getF0Floor());

        // Compute FFT size
        int fft_size = World.GetFFTSizeForCheapTrick(getSampleRate(), ch_opt);

        D4COption d4c_opt = new D4COption();
        World.InitializeD4COption(d4c_opt);

        // Allocate memory
        SWIGTYPE_p_p_double ap_tmp = World.new_double_p_array(f0_length);
        for (int t=0; t<f0_length; t++)
            World.double_p_array_setitem(ap_tmp, t,
                                         World.new_double_array(fft_size/2+1));

        // Extract aperiodicity
        World.D4C(x, x_length,
                  getSampleRate(), time_axis,
                  f0_cached, f0_length,
                  fft_size, d4c_opt,
                  ap_tmp);

        // Free the memory while generating the java array
        double[][] ap = new double[f0_length][fft_size/2+1];
        for (int t=0; t<f0_length; t++) {
            SWIGTYPE_p_double tmp = World.double_p_array_getitem(ap_tmp, t);

            for (int i=0; i<ap[0].length; i++) {
                ap[t][i] = World.double_array_getitem(tmp, i);
            }

            World.delete_double_array(tmp);
        }
        World.delete_double_p_array(ap_tmp);

        return ap;
    }

    /*****************************************************************************************************
     ** Synthesis entry part
     *****************************************************************************************************/

    /**
     *  Method to generate an audio based on given vocoder parameters.
     *  Should be called if the object is in synthesis mode!
     *
     *  @param f0 the F0
     *  @param sp the spectrum
     *  @param ap the aperiodicity
     *  @return the filled audioinputstream containing the rendered results
     */
    public AudioInputStream synthesis(double[] f0, double[][] sp, double[][] ap) {
        int fft_len = (sp[0].length - 1) * 2;

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
            // Create row
            SWIGTYPE_p_double row = World.new_double_array(ap[0].length);

            // Set values
            for (int i=0; i<ap[t].length; i++) {
                World.double_array_setitem(row, i, ap[t][i]);
            }

            World.double_p_array_setitem(ap_s, t, row);
        }

        // Synthesis
        int y_length =  (int)(f0.length * frame_period / 1000.0 * sample_rate);
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

        // Free memory
        for (int t=0; t<f0.length; t++) {
            World.delete_double_array(World.double_p_array_getitem(sp_s, t));
            World.delete_double_array(World.double_p_array_getitem(ap_s, t));
        }
        World.delete_double_p_array(ap_s);
        World.delete_double_p_array(sp_s);
        World.delete_double_array(f0_s);
        World.delete_double_array(y_s);

        // Generate audio inputstream
        AudioFormat format = new AudioFormat(sample_rate, 16, 1, true, false);   // use 16-bit audio, mono, signed PCM, little Endian
        byte[] data = new byte[2 * y.length];
        for (int i = 0; i < y.length; i++) {
            int temp = (int) Math.round(y[i]); // (short) (((float)y[i]) * Short.MAX_VALUE);
            data[2*i + 0] = (byte) temp;
            data[2*i + 1] = (byte) (temp >> 8);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        AudioInputStream ais = new AudioInputStream(bais, format, y.length);

        return ais;
    }

    /*****************************************************************************************************
     ** Accessors
     *****************************************************************************************************/

    /**
     *  Accessor to get the frame period
     *
     *  @return the frame period
     */
    public double getFramePeriod() {
        return frame_period;
    }

    /**
     *  Accessor to set a new frame period (should be used before calling the analysis/synthesis methods)
     *
     *  @param frame_period the new frame period
     */
    public void setFramePeriod(double frame_period) {
        this.frame_period = frame_period;
    }

    /**
     *  Accessor to get the sample rate
     *
     *  @return the sample rate in Hz
     */
    public int getSampleRate() {
        return sample_rate;
    }

    /**
     *  Accessor to set a new sample rate for the synthesis mode! This should not be used in analysis mode!
     *
     *  @param sample_rate the new sample rate in Hz
     */
    public void setSampleRate(int sample_rate) {
        this.sample_rate = sample_rate;
    }

    /**
     *  Accessor to get the compenstation lifter parameter used in analysis mode. This parameter s
     *  ignored in synthesis mode.
     *
     *  @return the compensation lifter parameter
     */
    public double getQ1() {
        return q1;
    }

    /**
     *  Accessor to set the compenstation lifter parameter used in analysis mode. This parameter is
     *  ignored in synthesis mode.
     *
     *  @param q1 the new compensation lifter parameter value
     */
    public void setQ1(double q1) {
        this.q1 = q1;
    }

    /**
     *  Accessor to get the threshold used for fixing the F0 contour in the analysis mode. This
     *  parameter is ignored in synthesis mode.
     *
     *  @return the threshold value
     */
    public double getF0AllowedRange() {
        return f0_allowed_range;
    }

    /**
     *  Accessor to set the threshold used for fixing the F0 contour in the analysis mode. This
     *  parameter is ignored in synthesis mode.
     *
     *  @param f0_allowed_range the new threshold value
     */
    public void setF0AllowedRange(double f0_allowed_range) {
        this.f0_allowed_range = f0_allowed_range;
    }

    /**
     *  Accessor to get the F0 floor value used in analysis mode. This parameter is ignored in
     *  synthesis mode.
     *
     *  @return the F0 floor value
     */
    public double getF0Floor() {
        return f0_floor;
    }

    /**
     *  Accessor to set the F0 floor value used in analysis mode. This parameter is ignored in
     *  synthesis mode.
     *
     *  @param f0_floor the new F0 floor value
     */
    public void setF0Floor(double f0_floor) {
        this.f0_floor = f0_floor;
    }

    /**
     *  Accessor to get the F0 ceil value used in analysis mode. This parameter is ignored in
     *  synthesis mode.
     *
     *  @return the F0 ceil value
     */
    public double getF0Ceil() {
        return f0_ceil;
    }

    /**
     *  Accessor to set the F0 ceil value used in analysis mode. This parameter is ignored in
     *  synthesis mode.
     *
     *  @param f0_ceil the new F0 ceil value
     */
    public void setF0Ceil(double f0_ceil) {
        this.f0_ceil = f0_ceil;
    }

    /**
     *  Accessor to get the speed of the speech.
     *
     *  @return the speed of the speech
     */
    public int getSpeed() {
        return speed;
    }


    /**
     *  Accessor to set the speed of the speech.
     *
     *  @param speed the new value of the speed of the speech
     */
    public void setSpeed(int speed) {
        this.speed = speed;
    }


    /*****************************************************************************************************
     ** Helpers
     *****************************************************************************************************/
    /**
     *   Helper to extract the samples in a double array for the audio input stream
     *
     *   @throws IOException if a problem with the stream occurs!
     */
    private void fromAISToDoubleArray() throws IOException {

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
            double v = ((short) (((data[2*i+1] & 0xFF) << 8) + (data[2*i] & 0xFF))) / ((double) Short.MAX_VALUE);
            World.double_array_setitem(x, i, v);
        }
    }


    /**
     *  Helper to clean the memory
     *
     */
    public void clean() {
        World.delete_double_array(f0_cached);
        World.delete_double_array(time_axis);
        World.delete_double_array(x);
    }

}
