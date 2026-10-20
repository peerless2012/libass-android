# The EFFECTS_ATLAS overlay,
# It can be build on Unix to run the units tests.

cmake_minimum_required(VERSION 3.22.1)
project(ass_overlay C)

if (ANDROID)
    if (NOT TARGET lib_ass::ass)
        find_package(lib_ass REQUIRED CONFIG)
    endif ()
    set(ASS_OVERLAY_LIBASS lib_ass::ass)
    set(ASS_OVERLAY_GL GLESv3)
else ()
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(LIBASS REQUIRED IMPORTED_TARGET libass)
    set(ASS_OVERLAY_LIBASS PkgConfig::LIBASS)
    pkg_check_modules(GLESV2 REQUIRED IMPORTED_TARGET glesv2)
    set(ASS_OVERLAY_GL PkgConfig::GLESV2)
endif ()

if (PROJECT_IS_TOP_LEVEL AND CMAKE_C_COMPILER_ID MATCHES "GNU|Clang")
    add_compile_options(-fsanitize=address,undefined -fno-sanitize-recover=all)
    add_link_options(-fsanitize=address,undefined)
endif ()

add_library(ass_overlay OBJECT AssSkyline.c AssAtlas.c AssOverlay.c AssOverlayGl.c)
target_include_directories(ass_overlay PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(ass_overlay PUBLIC ${ASS_OVERLAY_LIBASS} PRIVATE ${ASS_OVERLAY_GL})
if (ANDROID)
    target_link_libraries(ass_overlay PRIVATE log)
endif ()

if (PROJECT_IS_TOP_LEVEL)
    enable_testing()
    add_subdirectory(tests)
endif ()
