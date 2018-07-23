%include carrays.i
%array_functions( double, double_array );
%array_functions( double*, double_p_array );

%{
#include "world/d4c.h"
#include "world/dio.h"
#include "world/cheaptrick.h"
#include "world/synthesis.h"
#include "world/stonemask.h"
%}


//-----------------------------------------------------------------------------
// D4C
//-----------------------------------------------------------------------------
typedef struct {
  double threshold;
} D4COption;

void D4C(const double *x, int x_length, int fs,
         const double *temporal_positions, const double *f0, int f0_length,
         int fft_size, const D4COption *option, double **aperiodicity);
void InitializeD4COption(D4COption *option);

//-----------------------------------------------------------------------------
// DIO
//-----------------------------------------------------------------------------
typedef struct {
  double f0_floor;
  double f0_ceil;
  double channels_in_octave;
  double frame_period;  // msec
  int speed;  // (1, 2, ..., 12)
  double allowed_range;  // Threshold used for fixing the F0 contour.
} DioOption;

void InitializeDioOption(DioOption *option);
void Dio(const double *x, int x_length, int fs, const DioOption *option,
         double *temporal_positions, double *f0);
int GetSamplesForDIO(int fs, int x_length, double frame_period);


//-----------------------------------------------------------------------------
// StoneMask
//-----------------------------------------------------------------------------
void StoneMask(const double *x, int x_length, int fs,
               const double *temporal_positions, const double *f0, int f0_length,
               double *refined_f0);

//-----------------------------------------------------------------------------
// Struct for CheapTrick
//-----------------------------------------------------------------------------
typedef struct {
  double q1;
  double f0_floor;
  int fft_size;
} CheapTrickOption;

void CheapTrick(const double *x, int x_length, int fs,
    const double *temporal_positions, const double *f0, int f0_length,
    const CheapTrickOption *option, double **spectrogram);
void InitializeCheapTrickOption(int fs, CheapTrickOption *option);
int GetFFTSizeForCheapTrick(int fs, const CheapTrickOption *option);

//-----------------------------------------------------------------------------
// Synthesis
//-----------------------------------------------------------------------------
void Synthesis(const double *f0, int f0_length,
               const double * const *spectrogram, const double * const *aperiodicity,
               int fft_size, double frame_period, int fs, int y_length, double *y);
