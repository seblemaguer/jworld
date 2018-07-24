[![Build Status](https://travis-ci.org/marytts/jworld.svg)](https://travis-ci.org/marytts/jworld)

# JWorld

A Java/JNI wrapper around the world vocoder.

The world vocoder source code can be found here: https://github.com/mmorise/World

## Prerequisites

- Ensure that you have updated Git submodules by running `git submodule update --init`.

- [SWIG] must be installed.
  - On Linux (Debian/Ubuntu), run `sudo apt install swig`.
  - On Mac OSX, use [Homebrew] and run `brew install swig`.

## Building

Just run `./gradlew b`

[SWIG]: http://www.swig.org/
[Homebrew]: https://brew.sh/
