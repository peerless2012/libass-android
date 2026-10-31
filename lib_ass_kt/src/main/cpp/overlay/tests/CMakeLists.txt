enable_language(CXX)

# Give the extracted files the extraction time, so a GoogleTest upgrade rebuilds everything it touches.
if (POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)
endif ()

include(FetchContent)
FetchContent_Declare(googletest
    URL https://github.com/google/googletest/releases/download/v1.18.0/googletest-1.18.0.tar.gz
    URL_HASH SHA256=6e3191c1455468b3fc35a417fb565c1c5071aee1b7e7f85e30cf48a98d37d8b5
)
set(BUILD_GMOCK OFF)
set(INSTALL_GTEST OFF)
FetchContent_MakeAvailable(googletest)
include(GoogleTest)

foreach (test AssAtlasTest AssOverlayGlTest AssOverlayTest AssSkylineTest)
    add_executable(${test} ${test}.cpp)
    target_link_libraries(${test} PRIVATE ass_overlay GTest::gtest_main)
    gtest_discover_tests(${test} PROPERTIES TIMEOUT 30)
endforeach ()

pkg_check_modules(EGL REQUIRED IMPORTED_TARGET egl)
foreach (test AssOverlayGlTest AssOverlayTest)
    target_sources(${test} PRIVATE AssGlTest.cpp)
    target_link_libraries(${test} PRIVATE ${ASS_OVERLAY_GL} PkgConfig::EGL)
endforeach ()
