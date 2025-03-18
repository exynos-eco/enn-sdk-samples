# ENN SDK Samples 2400

## Introduction
|Sample Name| Description                                                                                              |
|-------------|----------------------------------------------------------------------------------------------------------|
|[Image Classification In Android](#image-classification-in-android)| Sample Android application to demonstrate the execution of `DenseNet121` model with ENN SDK              |
|[Object Detection In Android](#object-detection-in-android)| Sample Android application to demonstrate the execution of `Detr resNet50 dc5` model with ENN SDK        |
|[Semantic Segmentation In Android](#semantic-segmentation-in-android)| Sample Android application to demonstrate the execution of `DdrNet23 slim` model with ENN SDK            |
|[Pose Estimation In Android](#pose-estimation-in-android)| Sample Android application to demonstrate the execution of `PoseNet_MobileNet` model with ENN SDK        |
|[Image Enhancement In Android](#image-enhancement-in-android)| Sample Android application to demonstrate the execution of `Real_esrgan_general_x4v3` model with ENN SDK |
|[Depth Estimation In Andriod](#depth-estimation-in-andriod)| Sample Android application to demonstrate the execution of `Midas_v2` model with ENN SDK                 | |

## Android (Kotlin) Samples
This section provides an overview of Android (Kotlin) sample applications.
Each sample application entry provides the details of the functionality of the sample application, its location, and instructions for running it.

***

### Image Classification In Android
This document explains Android Sample Application operates using the [DenseNet121](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/118f8cc6-f251-43b7-b8c2-ec77a3c50fda) model optimized for Exynos hardware.

#### Functionality
This application classifies objects in images that are either from stored image files or those captured through a camera.
The classified items, corresponding scores, and inference time are displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/image-classification` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the Device Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the depth estimation project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

***

### Object Detection In Android
This document describes a method to operate Android sample application using the [Detr_resNet50_dc5](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/a631921e-dc8b-46cb-ac17-d23c5a54db26) model that is optimized for Exynos hardware.

#### Functionality
This sample application identifies objects in images that are either from stored image files or those captured through the camera.
The detected objects are highlighted with bounding boxes, and the label and score of each object are displayed.
Additionally, the inference time is displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/object-detection` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the Device Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the depth estimation project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

### Semantic Segmentation In Android
This document describes a method to operate Android sample application operates using the [DdrNet23_slim](https://prd.ai-studio-farm.com/kr/solution/ai/models/detail/994cb06f-b886-4fb6-b8e9-8b4efdc8baee) model that is optimized for Exynos hardware.

#### Functionality
This sample application provides semantic segmentation results for images that are either from stored image files or those captured through the camera.
Each pixel of the segmented object is overlaid with a color corresponding to its label, thereby offering a visual representation of the classification.

#### Location
The sample is available in the `enn-sdk-samples-2400/semantic-segmentation` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the Device Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the depth estimation project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

***

### Pose Estimation In Android
This document describes a method to operate Android sample application using the [Posenet_MobileNet](https://prd.ai-studio-farm.com/kr/solution/ai/models/detail/f40473cb-a6e6-42a3-a50a-daf428273eab) model that is optimized for Exynos hardware.

#### Functionality
This application detects key points in images that are either from stored image files or those captured through the camera and automatically measures joint positions.
Additionally, the inference time is displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/pose-estimation` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the Device Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the depth estimation project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory within the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

***

### Image Enhancement In Android
This document describes a method to operate Android sample application using the [Real_esrgan_general_x4v3](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/36ad7134-5621-48b2-8ddf-e4889417f6ef) model that is optimized for Exynos hardware.

#### Functionality
This application enhances the low-light quality of images that are either from stored image files or those captured through the camera.
Additionally, the inference time is displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/image-enhancement` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the Device Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the depth estimation project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

***

### Depth Estimation In Android
This document describes a method to operate Android sample application operates using the [Midas_v2](https://prd.ai-studio-farm.com/kr/solution/ai/models/detail/73bcae23-4a07-4df9-b0c5-80504427c11c) model that is optimized for Exynos hardware.

#### Functionality
This application receives input that are either from an image file or those captured through a camera.
A color representing the estimated distance is overlaid on each pixel that, provides a visual representation of depth.
Additionally, the inference time is displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/depth-estimation` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no available device to run the application, you can use the real devices provided in the Device Farm.
    For guidance on connecting a device to Android Studio, refer to the [Documentation](https://prd.ai-studio-farm.com/global/development/enn-sdk/document/documentation "...") menu under AI Studio Farm → 2. Device Remote Control → 2-7 ADB Proxy Guide.
3.  Push a sample image for testing to the following path using adb push.
4.  After confirming a proper connection with Device Farm, select Tools -> Device Manager in Android Studio.
5.  Next that check if the physical device is properly connected.## Getting Started

Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the Device Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the depth estimation project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

***