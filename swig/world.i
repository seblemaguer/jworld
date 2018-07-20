%include carrays.i
%array_functions( double, double_array )
%array_functions( double*, double_p_array );

%{
#include "world/synthesis.h"
%}


void Synthesis(const double *f0, int f0_length,
               const double * const *spectrogram, const double * const *aperiodicity,
               int fft_size, double frame_period, int fs, int y_length, double *y);
