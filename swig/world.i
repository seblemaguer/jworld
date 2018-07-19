%module World
%{
  /* Put header files here or function declarations like below */
  extern void Synthesis(const double *f0, int f0_length,
                        const double * const *spectrogram, const double * const *aperiodicity,
                        int fft_size, double frame_period, int fs, int y_length, double *y);
%}

%include carrays.i
%array_functions( double, double_array )


/* Put header files here or function declarations like below */
extern void Synthesis(const double *f0, int f0_length,
                      const double * const *spectrogram, const double * const *aperiodicity,
                      int fft_size, double frame_period, int fs, int y_length, double *y);
