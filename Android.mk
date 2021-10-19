LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PACKAGE_NAME := Torch

LOCAL_SYSTEM_EXT_MODULE := true

LOCAL_CERTIFICATE := platform

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_STATIC_ANDROID_LIBRARIES := \
        androidx.appcompat_appcompat \
        com.google.android.material_material

LOCAL_SRC_FILES := \
     $(call all-java-files-under, app/src/main/java)

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/app/src/main/res

LOCAL_MANIFEST_FILE := app/src/main/AndroidManifest.xml

include $(BUILD_PACKAGE)
