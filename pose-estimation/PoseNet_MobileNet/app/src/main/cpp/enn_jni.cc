#include <jni.h>
#include <iostream>
#include <android/log.h>
#include <vector>
#include "include/enn_api-public_ndk_v1.hpp"
#include "include/enn_api-type_ndk_v1.h"

#define LOG_TAG "EnnJNI"


jobject EnnBufferPtrAndNumberOfBuffersInfoToBufferSetInfo(
        JNIEnv *env,
        EnnBufferPtr *buffer_set,
        NumberOfBuffersInfo buffers_info
) {
    jclass bufferSetInfo = env->FindClass("com/samsung/poseestimation/enn_type/BufferSetInfo");
    jmethodID constructor = env->GetMethodID(bufferSetInfo, "<init>", "()V");

    jobject jobj = env->NewObject(bufferSetInfo, constructor);
    jfieldID buffer_setID = env->GetFieldID(bufferSetInfo, "buffer_set", "J");
    jfieldID n_in_bufID = env->GetFieldID(bufferSetInfo, "n_in_buf", "I");
    jfieldID n_out_bufID = env->GetFieldID(bufferSetInfo, "n_out_buf", "I");

    env->SetLongField(jobj, buffer_setID, reinterpret_cast<jlong>(buffer_set));
    env->SetIntField(jobj, n_in_bufID, (int) buffers_info.n_in_buf);
    env->SetIntField(jobj, n_out_bufID, (int) buffers_info.n_out_buf);

    return jobj;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennInitialize(
        JNIEnv *env,
        jobject thiz
) {
    if (enn::api::EnnInitialize()) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EnnInitialize Failed");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennDeinitialize(
        JNIEnv *env,
        jobject thiz
) {
    if (enn::api::EnnDeinitialize()) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EnnDeinitialize Failed");
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennOpenModel(
        JNIEnv *env,
        jobject thiz,
        jstring j_filename
) {
    EnnModelId model_id;
    const char *filename = env->GetStringUTFChars(j_filename, 0);

    if (enn::api::EnnOpenModel(filename, &model_id)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EnnOpenModel of [%s] Failed", filename);
    }

    return static_cast<jlong>(model_id);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennCloseModel(
        JNIEnv *env,
        jobject thiz,
        jlong model_id
) {
    if (enn::api::EnnCloseModel(model_id)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EnnCloseModel Failed");
    }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennAllocateAllBuffers(
        JNIEnv *env,
        jobject thiz,
        jlong model_id
) {
    EnnBufferPtr *buffer_set;
    NumberOfBuffersInfo buffers_info;

    if (enn::api::EnnAllocateAllBuffers(model_id, &buffer_set, &buffers_info)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EnnAllocateAllBuffers Failed");
    }

    return EnnBufferPtrAndNumberOfBuffersInfoToBufferSetInfo(env, buffer_set, buffers_info);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennReleaseBuffers(
        JNIEnv *env,
        jobject thiz,
        jlong j_buffers_set,
        jint buffer_size
) {
    auto *buffer_set = reinterpret_cast<EnnBufferPtr *>(j_buffers_set);

    if (enn::api::EnnReleaseBuffers(buffer_set, buffer_size)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EnnReleaseBuffers Failed");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennExecute(
        JNIEnv *env,
        jobject thiz,
        jlong model_id
) {
    if (enn::api::EnnExecuteModel(model_id)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "EnnExecuteModel Failed");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennMemcpyHostToDevice(
        JNIEnv *env,
        jobject thiz,
        jlong j_buffer_set,
        jint layer_number,
        jbyteArray j_data
) {
    auto *buffer_set = reinterpret_cast<EnnBufferPtr *>(j_buffer_set);
    size_t data_length = env->GetArrayLength(j_data);
    jbyte *data = env->GetByteArrayElements(j_data, nullptr);

    memcpy(
            (buffer_set[layer_number]->va),
            (data),
            data_length
    );
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_samsung_poseestimation_executor_ModelExecutor_ennMemcpyDeviceToHost(
        JNIEnv *env,
        jobject thiz,
        jlong j_buffer_set,
        jint layer_number
) {
    auto *buffer_set = reinterpret_cast<EnnBufferPtr *>(j_buffer_set);
    size_t data_length = buffer_set[layer_number]->size;
    jbyteArray data = env->NewByteArray(data_length);

    env->SetByteArrayRegion(
            data,
            0,
            data_length,
            reinterpret_cast<jbyte *>(buffer_set[layer_number]->va)
    );

    return data;
}