#ASS

## prepare
* install `libtool`
* install `perl`
* install `ninja-build`

## config 
* cd ./src/main/cpp/libass-cmake/src/unibreak && ./autogen.sh
* cd ./src/main/cpp/libass-cmake/src/fribidi && ./autogen.sh
* cd ./src/main/cpp/libass-cmake/src/ass && make ./autogen.sh

## clean
* cd ./src/main/cpp/libass-cmake/src/unibreak && make distclean
* cd ./src/main/cpp/libass-cmake/src/fribidi && make distclean