#ifndef LIBASS_ANDROID_ASS_OVERLAY_LOG_H
#define LIBASS_ANDROID_ASS_OVERLAY_LOG_H

#define ASS_OVERLAY_LOG_TAG "AssOverlay"

#ifdef __ANDROID__
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, ASS_OVERLAY_LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, ASS_OVERLAY_LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, ASS_OVERLAY_LOG_TAG, __VA_ARGS__)
#else
// Off Android (unit tests, desktop builds), logs go to stderr in logcat's "level/tag: message" form.
#include <stdio.h>

#define ASS_OVERLAY_LOG(level, ...) \
    (fprintf(stderr, level "/" ASS_OVERLAY_LOG_TAG ": "), fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#define LOGI(...) ASS_OVERLAY_LOG("I", __VA_ARGS__)
#define LOGW(...) ASS_OVERLAY_LOG("W", __VA_ARGS__)
#define LOGE(...) ASS_OVERLAY_LOG("E", __VA_ARGS__)
#endif

#endif // LIBASS_ANDROID_ASS_OVERLAY_LOG_H
