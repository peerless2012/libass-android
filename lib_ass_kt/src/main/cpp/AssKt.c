#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "ass/ass.h"
#include "fontconfig/fontconfig.h"

#define LOG_TAG "SubtitleRenderer"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

jlong nativeAssInit(JNIEnv* env, jclass clazz) {
    FcInit();
    ASS_Library* assLibrary = ass_library_init();
    ass_set_fonts_dir(assLibrary, "/system/fonts");
    ass_set_extract_fonts(assLibrary, 1);
    return (jlong) assLibrary;
}

void nativeAssAddFont(JNIEnv* env, jclass clazz, jlong ass, jstring name, jbyteArray byteArray) {
    jsize length = (*env)->GetArrayLength(env, byteArray);

    jbyte* bytePtr = (*env)->GetByteArrayElements(env, byteArray, NULL);

    if (bytePtr == NULL) {
        return;
    }
    const char * cName = (*env)->GetStringUTFChars(env, name, NULL);
    ass_add_font(((ASS_Library *) ass), cName, bytePtr, length);
    (*env)->ReleaseByteArrayElements(env, byteArray, bytePtr, 0);
}

void nativeAssClearFont(JNIEnv* env, jclass clazz, jlong ass) {
    ass_clear_fonts((ASS_Library *) ass);
}

void nativeAssDeinit(JNIEnv* env, jclass clazz, jlong ass) {
    if (ass) {
        ass_library_done((ASS_Library *) ass);
    }
    FcFini();
}

static JNINativeMethod method_table[] = {
        {"nativeAssInit", "()J", (void*)nativeAssInit},
        {"nativeAssAddFont", "(JLjava/lang/String;[B)V", (void*) nativeAssAddFont},
        {"nativeAssClearFont", "(J)V", (void*) nativeAssClearFont},
        {"nativeAssDeinit", "(J)V", (void*)nativeAssDeinit}
};

jlong nativeAssTrackInit(JNIEnv* env, jclass clazz, jlong ass) {
    return (jlong) ass_new_track((ASS_Library *) ass);
}

jint nativeAssTrackGetWidth(JNIEnv* env, jclass clazz, jlong track) {
    return ((ASS_Track *) track)->PlayResX;
}

jobjectArray nativeAssTrackGetEvents(JNIEnv* env, jclass clazz, jlong track) {
    jclass eventClass = (*env)->FindClass(env, "io/github/peerless2012/ass/AssEvent");
    if (eventClass == NULL) {
        return NULL;
    }

    jmethodID constructor = (*env)->GetMethodID(env, eventClass, "<init>", "(JJIIILjava/lang/String;IIILjava/lang/String;Ljava/lang/String;)V");
    if (constructor == NULL) {
        return NULL;
    }
    ASS_Track *assTrack = (ASS_Track *) track;

    if (assTrack->n_events <= 0) {
        return NULL;
    }

    jobjectArray eventArray = (*env)->NewObjectArray(env, assTrack->n_events, eventClass, NULL);
    if (eventArray == NULL) {
        return NULL;
    }
    for (int i = 0; i < assTrack->n_events; ++i) {
        ASS_Event assEvent = assTrack->events[i];
        jstring name = (*env)->NewStringUTF(env, assEvent.Name ? assEvent.Name : "");
        jstring effect = (*env)->NewStringUTF(env, assEvent.Effect ? assEvent.Effect : "");
        jstring text = (*env)->NewStringUTF(env, assEvent.Text ? assEvent.Text : "");

        jobject javaEvent = (*env)->NewObject(env, eventClass, constructor,
                                              (jlong) assEvent.Start,
                                              (jlong) assEvent.Duration,
                                              (jint) assEvent.ReadOrder,
                                              (jint) assEvent.Layer,
                                              (jint) assEvent.Style,
                                              name,
                                              (jint) assEvent.MarginL,
                                              (jint) assEvent.MarginR,
                                              (jint) assEvent.MarginV,
                                              effect,
                                              text);

        (*env)->DeleteLocalRef(env, name);
        (*env)->DeleteLocalRef(env, effect);
        (*env)->DeleteLocalRef(env, text);

        (*env)->SetObjectArrayElement(env, eventArray, i, javaEvent);
    }
    return eventArray;
}

void nativeAssTrackClearEvents(JNIEnv* env, jclass clazz, jlong track) {
    ASS_Track* tr = (ASS_Track *) track;
    for (int i = 0; i < tr->n_events; i++) {
        ass_free_event(tr, i);
    }
    tr->n_events = 0;
}

jint nativeAssTrackGetHeight(JNIEnv* env, jclass clazz, jlong track) {
    return ((ASS_Track *) track)->PlayResY;
}

void nativeAssTrackReadBuffer(JNIEnv* env, jclass clazz, jlong track, jbyteArray buffer, jint offset, jint length) {
    jboolean isCopy;
    jbyte* elements = (*env)->GetByteArrayElements(env, buffer, &isCopy);
    if (elements == NULL) {
        return;
    }
    ass_process_data((ASS_Track *) track, elements + offset, length);
    (*env)->ReleaseByteArrayElements(env, buffer, elements, 0);
}

void nativeAssTrackReadChunk(JNIEnv* env, jclass clazz, jlong track, jlong start, jlong duration, jbyteArray buffer, jint offset, jint length) {
    jboolean isCopy;
    jbyte* elements = (*env)->GetByteArrayElements(env, buffer, &isCopy);
    if (elements == NULL) {
        return;
    }
    ass_process_chunk((ASS_Track *) track, elements + offset, length, start, duration);
    (*env)->ReleaseByteArrayElements(env, buffer, elements, 0);
}

void nativeAssTrackDeinit(JNIEnv* env, jclass clazz, jlong track) {
    ass_free_track((ASS_Track *) track);
}


static JNINativeMethod trackMethodTable[] = {
        {"nativeAssTrackInit", "(J)J", (void*)nativeAssTrackInit},
        {"nativeAssTrackGetWidth", "(J)I", (void*) nativeAssTrackGetWidth},
        {"nativeAssTrackGetHeight", "(J)I", (void*) nativeAssTrackGetHeight},
        {"nativeAssTrackGetEvents", "(J)[Lio/github/peerless2012/ass/AssEvent;", (void*) nativeAssTrackGetEvents},
        {"nativeAssTrackClearEvents", "(J)V", (void*) nativeAssTrackClearEvents},
        {"nativeAssTrackReadBuffer", "(J[BII)V", (void*)nativeAssTrackReadBuffer},
        {"nativeAssTrackReadChunk", "(JJJ[BII)V", (void*)nativeAssTrackReadChunk},
        {"nativeAssTrackDeinit", "(J)V", (void*)nativeAssTrackDeinit}
};

jlong nativeAssRenderInit(JNIEnv* env, jclass clazz, jlong ass) {
    ASS_Renderer *assRenderer = ass_renderer_init((ASS_Library *) ass);
    ass_set_fonts(assRenderer, "/system/fonts/NotoSansCJK-Regular.ttc", "sans-serif", 1, NULL, 1);
//    ass_set_message_cb(ass_library, [](int level, const char *fmt, va_list args, void *data) {
//        vprintf(fmt, args);
//    }, NULL);
    return (jlong) assRenderer;
}

void nativeAssRenderSetFontScale(JNIEnv* env, jclass clazz, jlong render, jfloat scale) {
    ass_set_font_scale((ASS_Renderer *) render, scale);
}

void nativeAssRenderSetFrameSize(JNIEnv* env, jclass clazz, jlong render, jint width, jint height) {
    ass_set_frame_size((ASS_Renderer *) render, width, height);
}

void nativeAssRenderSetStorageSize(JNIEnv* env, jclass clazz, jlong render, jint width, jint height) {
    ass_set_storage_size((ASS_Renderer *) render, width, height);
}

jobject createBitmap(JNIEnv* env, const ASS_Image* image) {
    jclass bitmapConfigClass = (*env)->FindClass(env, "android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = (*env)->GetStaticFieldID(env, bitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888 = (*env)->GetStaticObjectField(env, bitmapConfigClass, argb8888FieldID);

    jclass bitmapClass = (*env)->FindClass(env, "android/graphics/Bitmap");
    jmethodID createBitmapMethodID = (*env)->GetStaticMethodID(env,
                                                               bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject bitmap = (*env)->CallStaticObjectMethod(env,
                                                    bitmapClass, createBitmapMethodID, image->w, image->h, argb8888);

    void* bitmapPixels;
    AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels);
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return NULL;
    }

    int stride = image->stride;
    unsigned int r = (image->color >> 24) & 0xFF;
    unsigned int g = (image->color >> 16) & 0xFF;
    unsigned int b = (image->color >> 8) & 0xFF;
    unsigned int opacity = 0xFF - image->color & 0xFF;
    for (int y = 0; y < image->h; ++y) {
        uint32_t *line = (uint32_t *)((char *)bitmapPixels + (y) * info.stride);
        for (int x = 0; x < image->w; ++x) {
            unsigned alpha = image->bitmap[y * stride + x];
            if (alpha > 0) {
                unsigned int a = (opacity * alpha) / 255;
                // premultiplied alpha
                float pm = a / 255.0f;
                // ABGR
                line[x] = a << 24 | ((unsigned int) (b * pm) << 16) | ((unsigned int) (g * pm) << 8) | (unsigned int) (r * pm);
            } else {
                line[x] = 0;
            }
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    return bitmap;
}

jobject createAlphaBitmap(JNIEnv* env, const ASS_Image* image) {
    jclass bitmapConfigClass = (*env)->FindClass(env, "android/graphics/Bitmap$Config");
    jfieldID alpha8FieldId = (*env)->GetStaticFieldID(env, bitmapConfigClass, "ALPHA_8", "Landroid/graphics/Bitmap$Config;");
    jobject alpha8 = (*env)->GetStaticObjectField(env, bitmapConfigClass, alpha8FieldId);

    jclass bitmapClass = (*env)->FindClass(env, "android/graphics/Bitmap");
    jmethodID createBitmapMethodID = (*env)->GetStaticMethodID(env,
                                                               bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject bitmap = (*env)->CallStaticObjectMethod(env,
                                                    bitmapClass, createBitmapMethodID, image->w, image->h, alpha8);

    void* bitmapPixels;
    AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels);
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return NULL;
    }

    if (info.stride == image->stride) {
        memcpy(bitmapPixels, image->bitmap, info.stride * info.height);
    } else {
        for (int y = 0; y < image->h; ++y) {
            char *dst = (char *) bitmapPixels + y * info.stride;
            char *src = (char *) image->bitmap + y * image->stride;
            memcpy(dst, src, image->w);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    return bitmap;
}

static int count_ass_images(ASS_Image *images) {
    int count = 0;
    for (ASS_Image *img = images; img != NULL; img = img->next) {
        count++;
    }
    return count;
}

jobject nativeAssRenderFrame(JNIEnv* env, jclass clazz, jlong render, jlong track, jlong time, jboolean onlyAlpha) {
    int changed;
    ASS_Image *image = ass_render_frame((ASS_Renderer *) render, (ASS_Track *) track, time, &changed);
    if (image == NULL) {
        return NULL;
    }
    jclass assFrameClass = (*env)->FindClass(env, "io/github/peerless2012/ass/AssFrame");
    jmethodID assFrameConstructor = (*env)->GetMethodID(env, assFrameClass, "<init>", "([Lio/github/peerless2012/ass/AssTex;I)V");

    if (changed == 0) {
        jobject res = (*env)->NewObject(env, assFrameClass, assFrameConstructor, NULL, changed);
        return res;
    }

    int size = count_ass_images(image);
    jclass assTexClass = (*env)->FindClass(env, "io/github/peerless2012/ass/AssTex");
    jmethodID assTexConstructor = (*env)->GetMethodID(env, assTexClass, "<init>", "(IILandroid/graphics/Bitmap;I)V");

    jobjectArray assTexArr = (*env)->NewObjectArray(env, size, assTexClass, NULL);
    if (assTexArr == NULL) {
        return NULL;
    }

    int index = 0;
    for (ASS_Image *img = image; img != NULL; img = img->next) {
        jobject bitmap = onlyAlpha ? createAlphaBitmap(env, img) : createBitmap(env, img);
        int32_t color = (int32_t) img->color;

        jobject assTexObject = (*env)->NewObject(env, assTexClass, assTexConstructor, img->dst_x, img->dst_y, bitmap, color);

        (*env)->SetObjectArrayElement(env, assTexArr, index, assTexObject);
        index++;
    }

    return (*env)->NewObject(env, assFrameClass, assFrameConstructor, assTexArr, changed);
}

void nativeAssRenderDeinit(JNIEnv* env, jclass clazz, jlong render) {
    if (render) {
        ass_renderer_done((ASS_Renderer *) render);
    }
}

static JNINativeMethod renderMethodTable[] = {
        {"nativeAssRenderInit", "(J)J", (void*)nativeAssRenderInit},
        {"nativeAssRenderSetFontScale", "(JF)V", (void*)nativeAssRenderSetFontScale},
        {"nativeAssRenderSetStorageSize", "(JII)V", (void*) nativeAssRenderSetStorageSize},
        {"nativeAssRenderSetFrameSize", "(JII)V", (void*)nativeAssRenderSetFrameSize},
        {"nativeAssRenderFrame", "(JJJZ)Lio/github/peerless2012/ass/AssFrame;", (void*) nativeAssRenderFrame},
        {"nativeAssRenderDeinit", "(J)V", (void*)nativeAssRenderDeinit},
};
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jint result = -1;

    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return -1;
    }
    jclass clazz = (*env)->FindClass(env, "io/github/peerless2012/ass/Ass");
    if (clazz == NULL) {
        return -1;
    }

    if ((*env)->RegisterNatives(env, clazz, method_table, sizeof(method_table) / sizeof(method_table[0])) < 0) {
        return -1;
    }

    clazz = (*env)->FindClass(env, "io/github/peerless2012/ass/AssTrack");
    if (clazz == NULL) {
        return -1;
    }

    if ((*env)->RegisterNatives(env, clazz, trackMethodTable, sizeof(trackMethodTable) / sizeof(trackMethodTable[0])) < 0) {
        return -1;
    }

    clazz = (*env)->FindClass(env, "io/github/peerless2012/ass/AssRender");
    if (clazz == NULL) {
        return -1;
    }

    if ((*env)->RegisterNatives(env, clazz, renderMethodTable, sizeof(renderMethodTable) / sizeof(renderMethodTable[0])) < 0) {
        return -1;
    }

    result = JNI_VERSION_1_6;
    return result;
}