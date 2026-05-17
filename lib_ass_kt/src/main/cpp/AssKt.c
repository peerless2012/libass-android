#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <jni.h>
#include "ass/ass.h"
#include "fontconfig/fontconfig.h"
#include "GLES2/gl2.h"
#include "GLES2/gl2ext.h"

#define LOG_TAG "SubtitleRenderer"

typedef struct {
    pthread_mutex_t mutex;
} AssContext;

void assMessageCallback(int level, const char *fmt, va_list args, void *data) {
    if (level > 4) return;

    if (level >= 2) {
        __android_log_vprint(ANDROID_LOG_WARN, LOG_TAG, fmt, args);
    } else {
        __android_log_vprint(ANDROID_LOG_ERROR, LOG_TAG, fmt, args);
    }
}

jlong nativeAssContextCreate(JNIEnv* env, jclass clazz) {
    AssContext *ctx = malloc(sizeof(AssContext));
    pthread_mutex_init(&ctx->mutex, NULL);
    return (jlong) ctx;
}

void nativeAssContextDestroy(JNIEnv* env, jclass clazz, jlong ctx) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_destroy(&assCtx->mutex);
    free(assCtx);
}

static JNINativeMethod contextMethodTable[] = {
        {"nativeAssContextCreate", "()J", (void*) nativeAssContextCreate},
        {"nativeAssContextDestroy", "(J)V", (void*) nativeAssContextDestroy},
};

jlong nativeAssInit(JNIEnv* env, jclass clazz, jlong ctx) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ASS_Library* assLibrary = ass_library_init();
    ass_set_message_cb(assLibrary, assMessageCallback, env);
    ass_set_extract_fonts(assLibrary, 1);
    pthread_mutex_unlock(&assCtx->mutex);
    return (jlong) assLibrary;
}

void nativeAssAddFont(JNIEnv* env, jclass clazz, jlong ctx, jlong ass, jstring name, jbyteArray byteArray) {
    AssContext *assCtx = (AssContext *) ctx;
    jsize length = (*env)->GetArrayLength(env, byteArray);

    jbyte* bytePtr = (*env)->GetByteArrayElements(env, byteArray, NULL);

    if (bytePtr == NULL) {
        return;
    }
    const char * cName = (*env)->GetStringUTFChars(env, name, NULL);
    pthread_mutex_lock(&assCtx->mutex);
    ass_add_font(((ASS_Library *) ass), cName, bytePtr, length);
    pthread_mutex_unlock(&assCtx->mutex);
    (*env)->ReleaseByteArrayElements(env, byteArray, bytePtr, 0);
    if (cName != NULL) {
        (*env)->ReleaseStringUTFChars(env, name, cName);
    }
}

void nativeAssClearFont(JNIEnv* env, jclass clazz, jlong ctx, jlong ass) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ass_clear_fonts((ASS_Library *) ass);
    pthread_mutex_unlock(&assCtx->mutex);
}

void nativeAssDeinit(JNIEnv* env, jclass clazz, jlong ctx, jlong ass) {
    if (ass) {
        AssContext *assCtx = (AssContext *) ctx;
        pthread_mutex_lock(&assCtx->mutex);
        ass_library_done((ASS_Library *) ass);
        pthread_mutex_unlock(&assCtx->mutex);
    }
}

static JNINativeMethod method_table[] = {
        {"nativeAssInit", "(J)J", (void*)nativeAssInit},
        {"nativeAssAddFont", "(JJLjava/lang/String;[B)V", (void*) nativeAssAddFont},
        {"nativeAssClearFont", "(JJ)V", (void*) nativeAssClearFont},
        {"nativeAssDeinit", "(JJ)V", (void*)nativeAssDeinit}
};

jlong nativeAssTrackInit(JNIEnv* env, jclass clazz, jlong ctx, jlong ass) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    jlong result = (jlong) ass_new_track((ASS_Library *) ass);
    pthread_mutex_unlock(&assCtx->mutex);
    return result;
}

jint nativeAssTrackGetWidth(JNIEnv* env, jclass clazz, jlong ctx, jlong track) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    jint result = ((ASS_Track *) track)->PlayResX;
    pthread_mutex_unlock(&assCtx->mutex);
    return result;
}

jobjectArray nativeAssTrackGetEvents(JNIEnv* env, jclass clazz, jlong ctx, jlong track) {
    jclass eventClass = (*env)->FindClass(env, "io/github/peerless2012/ass/AssEvent");
    if (eventClass == NULL) {
        return NULL;
    }

    jmethodID constructor = (*env)->GetMethodID(env, eventClass, "<init>", "(JJIIILjava/lang/String;IIILjava/lang/String;Ljava/lang/String;)V");
    if (constructor == NULL) {
        return NULL;
    }

    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ASS_Track *assTrack = (ASS_Track *) track;

    if (assTrack->n_events <= 0) {
        pthread_mutex_unlock(&assCtx->mutex);
        return NULL;
    }

    jobjectArray eventArray = (*env)->NewObjectArray(env, assTrack->n_events, eventClass, NULL);
    if (eventArray == NULL) {
        pthread_mutex_unlock(&assCtx->mutex);
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
    pthread_mutex_unlock(&assCtx->mutex);
    return eventArray;
}

void nativeAssTrackClearEvents(JNIEnv* env, jclass clazz, jlong ctx, jlong track) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ASS_Track* tr = (ASS_Track *) track;
    for (int i = 0; i < tr->n_events; i++) {
        ass_free_event(tr, i);
    }
    tr->n_events = 0;
    pthread_mutex_unlock(&assCtx->mutex);
}

jint nativeAssTrackGetHeight(JNIEnv* env, jclass clazz, jlong ctx, jlong track) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    jint result = ((ASS_Track *) track)->PlayResY;
    pthread_mutex_unlock(&assCtx->mutex);
    return result;
}

void nativeAssTrackReadBuffer(JNIEnv* env, jclass clazz, jlong ctx, jlong track, jbyteArray buffer, jint offset, jint length) {
    jboolean isCopy;
    jbyte* elements = (*env)->GetByteArrayElements(env, buffer, &isCopy);
    if (elements == NULL) {
        return;
    }
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ass_process_data((ASS_Track *) track, elements + offset, length);
    pthread_mutex_unlock(&assCtx->mutex);
    (*env)->ReleaseByteArrayElements(env, buffer, elements, 0);
}

void nativeAssTrackReadChunk(JNIEnv* env, jclass clazz, jlong ctx, jlong track, jlong start, jlong duration, jbyteArray buffer, jint offset, jint length) {
    jboolean isCopy;
    jbyte* elements = (*env)->GetByteArrayElements(env, buffer, &isCopy);
    if (elements == NULL) {
        return;
    }
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ass_process_chunk((ASS_Track *) track, elements + offset, length, start, duration);
    pthread_mutex_unlock(&assCtx->mutex);
    (*env)->ReleaseByteArrayElements(env, buffer, elements, 0);
}

void nativeAssTrackDeinit(JNIEnv* env, jclass clazz, jlong ctx, jlong track) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ass_free_track((ASS_Track *) track);
    pthread_mutex_unlock(&assCtx->mutex);
}


static JNINativeMethod trackMethodTable[] = {
        {"nativeAssTrackInit", "(JJ)J", (void*)nativeAssTrackInit},
        {"nativeAssTrackGetWidth", "(JJ)I", (void*) nativeAssTrackGetWidth},
        {"nativeAssTrackGetHeight", "(JJ)I", (void*) nativeAssTrackGetHeight},
        {"nativeAssTrackGetEvents", "(JJ)[Lio/github/peerless2012/ass/AssEvent;", (void*) nativeAssTrackGetEvents},
        {"nativeAssTrackClearEvents", "(JJ)V", (void*) nativeAssTrackClearEvents},
        {"nativeAssTrackReadBuffer", "(JJ[BII)V", (void*)nativeAssTrackReadBuffer},
        {"nativeAssTrackReadChunk", "(JJJJ[BII)V", (void*)nativeAssTrackReadChunk},
        {"nativeAssTrackDeinit", "(JJ)V", (void*)nativeAssTrackDeinit}
};

jlong nativeAssRenderInit(JNIEnv* env, jclass clazz, jlong ctx, jlong ass) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ASS_Renderer *assRenderer = ass_renderer_init((ASS_Library *) ass);
    ass_set_fonts(assRenderer, NULL, "sans-serif", ASS_FONTPROVIDER_FONTCONFIG, NULL, 1);
    pthread_mutex_unlock(&assCtx->mutex);
    return (jlong) assRenderer;
}

void nativeAssRenderSetFontScale(JNIEnv* env, jclass clazz, jlong ctx, jlong render, jfloat scale) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ass_set_font_scale((ASS_Renderer *) render, scale);
    pthread_mutex_unlock(&assCtx->mutex);
}

void nativeAssRenderSetCacheLimit(JNIEnv* env, jclass clazz, jlong ctx, jlong render, jint glyphMax, jint bitmapMaxSize) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ass_set_cache_limits((ASS_Renderer *) render, glyphMax, bitmapMaxSize);
    pthread_mutex_unlock(&assCtx->mutex);
}

void nativeAssRenderSetFrameSize(JNIEnv* env, jclass clazz, jlong ctx, jlong render, jint width, jint height) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ass_set_frame_size((ASS_Renderer *) render, width, height);
    pthread_mutex_unlock(&assCtx->mutex);
}

void nativeAssRenderSetStorageSize(JNIEnv* env, jclass clazz, jlong ctx, jlong render, jint width, jint height) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    ass_set_storage_size((ASS_Renderer *) render, width, height);
    pthread_mutex_unlock(&assCtx->mutex);
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

jint createTexture(JNIEnv* env, const ASS_Image* image) {
    GLuint texture;
    glGenTextures(1, &texture);
    if (texture <= 0) return 0;
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glPixelStorei(GL_UNPACK_ROW_LENGTH_EXT, image->stride);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, image->w, image->h, 0, GL_ALPHA, GL_UNSIGNED_BYTE, image->bitmap);
    glPixelStorei(GL_UNPACK_ROW_LENGTH_EXT, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    return texture;
}

static int count_ass_images(ASS_Image *images) {
    int count = 0;
    for (ASS_Image *img = images; img != NULL; img = img->next) {
        count++;
    }
    return count;
}

jobject nativeAssRenderFrame(JNIEnv* env, jclass clazz, jlong ctx, jlong render, jlong track, jlong time, jint type) {
    AssContext *assCtx = (AssContext *) ctx;
    pthread_mutex_lock(&assCtx->mutex);
    int changed;
    ASS_Image *image = ass_render_frame((ASS_Renderer *) render, (ASS_Track *) track, time, &changed);
    if (image == NULL) {
        pthread_mutex_unlock(&assCtx->mutex);
        return NULL;
    }

    jclass assFrameClass = (*env)->FindClass(env, "io/github/peerless2012/ass/AssFrame");
    jmethodID assFrameConstructor = (*env)->GetMethodID(env, assFrameClass, "<init>", "([Lio/github/peerless2012/ass/AssTex;I)V");

    if (changed == 0) {
        pthread_mutex_unlock(&assCtx->mutex);
        jobject res = (*env)->NewObject(env, assFrameClass, assFrameConstructor, NULL, changed);
        return res;
    }

    int size = count_ass_images(image);
    jclass assTexClass = (*env)->FindClass(env, "io/github/peerless2012/ass/AssTex");
    jmethodID assTexConstructor = (*env)->GetMethodID(env, assTexClass, "<init>", "(IIIIILandroid/graphics/Bitmap;I)V");

    jobjectArray assTexArr = (*env)->NewObjectArray(env, size, assTexClass, NULL);
    if (assTexArr == NULL) {
        pthread_mutex_unlock(&assCtx->mutex);
        return NULL;
    }

    int index = 0;
    for (ASS_Image *img = image; img != NULL; img = img->next) {
        jobject bitmap = NULL;
        jint tex = 0;
        if (img->w > 0 && img->h > 0) {
            if (type == 0) {
                bitmap = createBitmap(env, img);
            } else if (type == 1) {
                bitmap = createAlphaBitmap(env, img);
            } else if (type == 2) {
               tex = createTexture(env, img);
            }
        }
        int32_t color = (int32_t) img->color;
        jobject assTexObject = (*env)->NewObject(env, assTexClass, assTexConstructor, img->dst_x, img->dst_y, img->w, img->h, color, bitmap, tex);
        (*env)->SetObjectArrayElement(env, assTexArr, index, assTexObject);
        (*env)->DeleteLocalRef(env, assTexObject);
        if (bitmap != NULL) {
            (*env)->DeleteLocalRef(env, bitmap);
        }
        index++;
    }
    pthread_mutex_unlock(&assCtx->mutex);

    return (*env)->NewObject(env, assFrameClass, assFrameConstructor, assTexArr, changed);
}

void nativeAssRenderDeinit(JNIEnv* env, jclass clazz, jlong ctx, jlong render) {
    if (render) {
        AssContext *assCtx = (AssContext *) ctx;
        pthread_mutex_lock(&assCtx->mutex);
        ass_renderer_done((ASS_Renderer *) render);
        pthread_mutex_unlock(&assCtx->mutex);
    }
}

static JNINativeMethod renderMethodTable[] = {
        {"nativeAssRenderInit", "(JJ)J", (void*)nativeAssRenderInit},
        {"nativeAssRenderSetFontScale", "(JJF)V", (void*)nativeAssRenderSetFontScale},
        {"nativeAssRenderSetCacheLimit", "(JJII)V", (void*)nativeAssRenderSetCacheLimit},
        {"nativeAssRenderSetStorageSize", "(JJII)V", (void*) nativeAssRenderSetStorageSize},
        {"nativeAssRenderSetFrameSize", "(JJII)V", (void*)nativeAssRenderSetFrameSize},
        {"nativeAssRenderFrame", "(JJJJI)Lio/github/peerless2012/ass/AssFrame;", (void*) nativeAssRenderFrame},
        {"nativeAssRenderDeinit", "(JJ)V", (void*)nativeAssRenderDeinit},
};
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jint result = -1;

    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return -1;
    }

    jclass clazz = (*env)->FindClass(env, "io/github/peerless2012/ass/AssContext");
    if (clazz == NULL) {
        return -1;
    }

    if ((*env)->RegisterNatives(env, clazz, contextMethodTable, sizeof(contextMethodTable) / sizeof(contextMethodTable[0])) < 0) {
        return -1;
    }

    clazz = (*env)->FindClass(env, "io/github/peerless2012/ass/Ass");
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