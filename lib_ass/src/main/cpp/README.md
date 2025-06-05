#ASS CMake

## How to build this module

1. prepare
    * install `libtool`
    * install `perl`
    * install `autopoint`
    * install `ninja-build`

2. config
    * cd ./libass-cmake/src/expat/expat && ./buildconf.sh
    * cd ./libass-cmake/src/unibreak && ./autogen.sh
    * cd ./libass-cmake/src/fribidi && ./autogen.sh
    * cd ./libass-cmake/src/fontconfig && ./autogen.sh
    * cd ./libass-cmake/src/ass && ./autogen.sh

3. clean
    * cd ./libass-cmake/src/unibreak && make distclean
    * cd ./libass-cmake/src/fribidi && make distclean