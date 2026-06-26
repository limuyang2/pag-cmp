#include <jni.h>

#include <cstdint>
#include <cstring>
#include <memory>
#include <string>

#include "pag/pag.h"

namespace {

class NativeHandle {
 public:
  virtual ~NativeHandle() = default;
};

class NativeComposition final : public NativeHandle {
 public:
  explicit NativeComposition(std::shared_ptr<pag::PAGFile> file) : file(std::move(file)) {
  }

  std::shared_ptr<pag::PAGFile> file;
};

class NativePlayer final : public NativeHandle {
 public:
  std::shared_ptr<pag::PAGPlayer> player = std::make_shared<pag::PAGPlayer>();
  std::shared_ptr<pag::PAGSurface> surface = nullptr;
  int surfaceWidth = 0;
  int surfaceHeight = 0;
};

template <typename T>
T* fromHandle(jlong handle) {
  auto* nativeHandle = reinterpret_cast<NativeHandle*>(static_cast<intptr_t>(handle));
  return dynamic_cast<T*>(nativeHandle);
}

NativeHandle* fromHandle(jlong handle) {
  return reinterpret_cast<NativeHandle*>(static_cast<intptr_t>(handle));
}

jlong toHandle(NativeHandle* handle) {
  return static_cast<jlong>(reinterpret_cast<intptr_t>(handle));
}

void throwIllegalArgument(JNIEnv* env, const char* message) {
  auto clazz = env->FindClass("java/lang/IllegalArgumentException");
  if (clazz != nullptr) {
    env->ThrowNew(clazz, message);
  }
}

void throwIllegalState(JNIEnv* env, const char* message) {
  auto clazz = env->FindClass("java/lang/IllegalStateException");
  if (clazz != nullptr) {
    env->ThrowNew(clazz, message);
  }
}

std::string toString(JNIEnv* env, jstring value) {
  if (value == nullptr) {
    return {};
  }
  const char* chars = env->GetStringUTFChars(value, nullptr);
  if (chars == nullptr) {
    return {};
  }
  std::string result(chars);
  env->ReleaseStringUTFChars(value, chars);
  return result;
}

pag::PAGScaleMode toScaleMode(jint value) {
  switch (value) {
    case 0:
      return pag::PAGScaleMode::None;
    case 1:
      return pag::PAGScaleMode::Stretch;
    case 2:
      return pag::PAGScaleMode::LetterBox;
    case 3:
      return pag::PAGScaleMode::Zoom;
    default:
      return pag::PAGScaleMode::LetterBox;
  }
}

bool ensureSurface(NativePlayer* nativePlayer, int width, int height) {
  if (nativePlayer->surface != nullptr && nativePlayer->surfaceWidth == width &&
      nativePlayer->surfaceHeight == height) {
    return true;
  }
  nativePlayer->player->setSurface(nullptr);
  nativePlayer->surface = pag::PAGSurface::MakeOffscreen(width, height);
  nativePlayer->surfaceWidth = width;
  nativePlayer->surfaceHeight = height;
  if (nativePlayer->surface == nullptr) {
    return false;
  }
  nativePlayer->player->setSurface(nativePlayer->surface);
  return true;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_loadFile(JNIEnv* env, jclass, jbyteArray bytes,
                                                          jstring filePath) {
  if (bytes == nullptr) {
    throwIllegalArgument(env, "bytes must not be null.");
    return 0;
  }
  auto length = env->GetArrayLength(bytes);
  if (length <= 0) {
    throwIllegalArgument(env, "PAG bytes must not be empty.");
    return 0;
  }

  jboolean copied = JNI_FALSE;
  auto* rawBytes = env->GetByteArrayElements(bytes, &copied);
  if (rawBytes == nullptr) {
    throwIllegalState(env, "Failed to access PAG bytes.");
    return 0;
  }

  auto path = toString(env, filePath);
  auto file = pag::PAGFile::Load(rawBytes, static_cast<size_t>(length), path);
  env->ReleaseByteArrayElements(bytes, rawBytes, JNI_ABORT);

  if (file == nullptr) {
    throwIllegalArgument(env, "Invalid PAG bytes.");
    return 0;
  }
  return toHandle(new NativeComposition(file));
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_createPlayer(JNIEnv*, jclass) {
  return toHandle(new NativePlayer());
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_compositionWidth(JNIEnv*, jclass, jlong handle) {
  auto* composition = fromHandle<NativeComposition>(handle);
  return composition == nullptr ? 0 : composition->file->width();
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_compositionHeight(JNIEnv*, jclass, jlong handle) {
  auto* composition = fromHandle<NativeComposition>(handle);
  return composition == nullptr ? 0 : composition->file->height();
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_compositionDurationUs(JNIEnv*, jclass,
                                                                       jlong handle) {
  auto* composition = fromHandle<NativeComposition>(handle);
  return composition == nullptr ? 0 : static_cast<jlong>(composition->file->duration());
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_setComposition(JNIEnv*, jclass, jlong playerHandle,
                                                                jlong compositionHandle) {
  auto* nativePlayer = fromHandle<NativePlayer>(playerHandle);
  auto* composition = fromHandle<NativeComposition>(compositionHandle);
  if (nativePlayer == nullptr) {
    return;
  }
  nativePlayer->player->setComposition(composition == nullptr ? nullptr : composition->file);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_setCacheEnabled(JNIEnv*, jclass,
                                                                 jlong playerHandle,
                                                                 jboolean cacheEnabled) {
  auto* nativePlayer = fromHandle<NativePlayer>(playerHandle);
  if (nativePlayer != nullptr) {
    nativePlayer->player->setCacheEnabled(cacheEnabled == JNI_TRUE);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_setVideoEnabled(JNIEnv*, jclass,
                                                                 jlong playerHandle,
                                                                 jboolean videoEnabled) {
  auto* nativePlayer = fromHandle<NativePlayer>(playerHandle);
  if (nativePlayer != nullptr) {
    nativePlayer->player->setVideoEnabled(videoEnabled == JNI_TRUE);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_setUseDiskCache(JNIEnv*, jclass,
                                                                 jlong playerHandle,
                                                                 jboolean useDiskCache) {
  auto* nativePlayer = fromHandle<NativePlayer>(playerHandle);
  if (nativePlayer != nullptr) {
    nativePlayer->player->setUseDiskCache(useDiskCache == JNI_TRUE);
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_renderToBgra(JNIEnv* env, jclass,
                                                              jlong playerHandle, jint width,
                                                              jint height, jdouble progress,
                                                              jint scaleMode,
                                                              jbyteArray pixels) {
  auto* nativePlayer = fromHandle<NativePlayer>(playerHandle);
  if (nativePlayer == nullptr) {
    throwIllegalArgument(env, "Invalid PAG player handle.");
    return JNI_FALSE;
  }
  if (width <= 0 || height <= 0) {
    throwIllegalArgument(env, "Render size must be positive.");
    return JNI_FALSE;
  }
  const auto requiredSize = width * height * 4;
  if (pixels == nullptr || env->GetArrayLength(pixels) < requiredSize) {
    throwIllegalArgument(env, "Pixel buffer is too small.");
    return JNI_FALSE;
  }
  if (!ensureSurface(nativePlayer, width, height)) {
    return JNI_FALSE;
  }

  nativePlayer->player->setProgress(progress);
  nativePlayer->player->setScaleMode(toScaleMode(scaleMode));
  // PAGPlayer::flush() can return false when the current content version has not changed.
  // The previous surface pixels are still valid in that case, so readPixels() is the real
  // success condition for this CPU readback bridge.
  nativePlayer->player->flush();

  jboolean copied = JNI_FALSE;
  auto* rawPixels = env->GetByteArrayElements(pixels, &copied);
  if (rawPixels == nullptr) {
    throwIllegalState(env, "Failed to access pixel buffer.");
    return JNI_FALSE;
  }
  const auto success = nativePlayer->surface->readPixels(
      pag::ColorType::BGRA_8888, pag::AlphaType::Premultiplied, rawPixels,
      static_cast<size_t>(width) * 4);
  env->ReleaseByteArrayElements(pixels, rawPixels, success ? 0 : JNI_ABORT);
  return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_libpag_cmp_JvmPagNative_release(JNIEnv*, jclass, jlong handle) {
  delete fromHandle(handle);
}
