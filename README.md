# ENN SDK Samples 2400

## Introduction
|Sample Name| Description                                                                                       |
|-------------|---------------------------------------------------------------------------------------------------|
|[Image Classification In Android](#image-classification-in-android)| Sample Android application to demonstrate the execution of `Densenet121` model with ENN SDK       |
|[Object Detection In Android](#object-detection-in-android)| Sample Android application to demonstrate the execution of `Detr_resnet50_dc5` model with ENN SDK |
|[Semantic Segmentation In Android](#semantic-segmentation-in-android)| Sample Android application to demonstrate the execution of `Ddrnet23_slim` model with ENN SDK     |
|[Pose Estimation In Android](#pose-estimation-in-android)| Sample Android application to demonstrate the execution of `PoseNet` model with ENN SDK           |
|[Image Enhancement In Android](#image-enhancement-in-android)| Sample Android application to demonstrate the execution of `Real_ESRGAN_General_x4v3` model with ENN SDK          |
|[Depth Estimation In Andriod](#depth-estimation-in-andriod)| Sample Android application to demonstrate the execution of `MiDaS v2` model with ENN SDK          | |

## Android (Kotlin) Samples
This section provides an overview of Android (Kotlin) sample applications.
Each sample application entry provides the details of the functionality of the sample application, its location, and instructions for running it.

***

### Image Classification In Android
This Application explains how a simple Android Sample Application operates using the [Densenet121](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/118f8cc6-f251-43b7-b8c2-ec77a3c50fda) model optimized for Exynos hardware.

#### Functionality
This application classifies objects in images either from stored image files or captured via the camera.
The classified items, corresponding scores, and inference time are displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/image-classification` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no available device to run the application, you can use the real devices provided in the Device Farm.
    For guidance on connecting a device to Android Studio, refer to the [Documentation](https://prd.ai-studio-farm.com/global/development/enn-sdk/document/documentation "...") menu under AI Studio Farm → 2. Device Remote Control → 2-7 ADB Proxy Guide.
3.  Push a sample image for testing to the following path using adb push.
4.  After confirming a proper connection with Device Farm, select Tools -> Device Manager in Android Studio.
5.  Next that check if the physical device is properly connected.
6.  Run the Image Classification project from the Sample Applications obtained via git clone in Android Studio.
7.  Upload the image data for inference and execute the application.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()`, `convertBitmapToFloatArray()` functions.

***

### Object Detection In Android
This Application explains how a simple Android Sample Application operates using the [Detr_resnet50_dc5](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/a631921e-dc8b-46cb-ac17-d23c5a54db26) model optimized for Exynos hardware.

#### Functionality
This Sample Application identifies objects in images either from stored image files or input via the camera. 
Detected objects are highlighted with bounding boxes, and the label and score of each object are displayed. 
Additionally, the inference time is shown at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/object-detection` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no available device to run the application, you can use the real devices provided in the Device Farm.
    For guidance on connecting a device to Android Studio, refer to the [Documentation](https://prd.ai-studio-farm.com/global/development/enn-sdk/document/documentation "...") menu under AI Studio Farm → 2. Device Remote Control → 2-7 ADB Proxy Guide.
3.  Push a sample image for testing to the following path using adb push.
4.  After confirming a proper connection with Device Farm, select Tools -> Device Manager in Android Studio.
5.  Next that check if the physical device is properly connected.
6.  Run the Object Detection project from the Sample Applications obtained via git clone in Android Studio.
7.  Upload the image data for inference and execute the application.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()`, `convertBitmapToFloatArray()` functions.

### Semantic Segmentation In Android
This Application explains how a simple Android Sample Application operates using the [Ddrnet23_slim](https://prd.ai-studio-farm.com/kr/solution/ai/models/detail/994cb06f-b886-4fb6-b8e9-8b4efdc8baee) model optimized for Exynos hardware.

#### Functionality
This Application provides segmentation results for images either from stored image files or captured via the camera. 
Each pixel of the segmented object is overlaid with a color corresponding to its label, offering a visual representation of the classification.

#### Location
The sample is available in the `enn-sdk-samples-2400/semantic-segmentation` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no available device to run the application, you can use the real devices provided in the Device Farm.
    For guidance on connecting a device to Android Studio, refer to the [Documentation](https://prd.ai-studio-farm.com/global/development/enn-sdk/document/documentation "...") menu under AI Studio Farm → 2. Device Remote Control → 2-7 ADB Proxy Guide.
3.  Push a sample image for testing to the following path using adb push.
4.  After confirming a proper connection with Device Farm, select Tools -> Device Manager in Android Studio.
5.  Next that check if the physical device is properly connected.
6.  Run the Object Detection project from the Sample Applications obtained via git clone in Android Studio.
7.  Upload the image data for inference and execute the application.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()`, `convertBitmapToFloatArray()` functions.

***

### Pose Estimation In Android
This Application explains how a simple Android Sample Application operates using the [Posenet](https://prd.ai-studio-farm.com/kr/solution/ai/models/detail/f40473cb-a6e6-42a3-a50a-daf428273eab) model optimized for Exynos hardware.

#### Functionality
This Application detects key points in images from stored image files or those captured via the camera and automatically measures joint positions. 
Additionally, the inference time is displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/pose-estimation` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no available device to run the application, you can use the real devices provided in the Device Farm.
    For guidance on connecting a device to Android Studio, refer to the [Documentation](https://prd.ai-studio-farm.com/global/development/enn-sdk/document/documentation "...") menu under AI Studio Farm → 2. Device Remote Control → 2-7 ADB Proxy Guide.
3.  Push a sample image for testing to the following path using adb push.
4.  After confirming a proper connection with Device Farm, select Tools -> Device Manager in Android Studio.
5.  Next that check if the physical device is properly connected.
6.  Run the Object Detection project from the Sample Applications obtained via git clone in Android Studio.
7.  Upload the image data for inference and execute the application.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory within the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()`, `convertBitmapToFloatArray()` functions.

***

### Image Enhancement In Android
This document explains how a simple Android Sample Application operates using the [Real_ESRGAN_General_x4v3](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/36ad7134-5621-48b2-8ddf-e4889417f6ef) model optimized for Exynos hardware.

#### Functionality
This Application enhances the low-light quality of images from stored image files or those captured via the camera.
Additionally, the inference time is displayed at the bottom of the application interface.

#### Location
The sample is available in the `enn-sdk-samples-2400/image-enhancement` directory within the [Github](https://github.com/exynos-eco/enn-sdk-samples-2400) repository.

#### Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no available device to run the application, you can use the real devices provided in the Device Farm.
    For guidance on connecting a device to Android Studio, refer to the [Documentation](https://prd.ai-studio-farm.com/global/development/enn-sdk/document/documentation "...") menu under AI Studio Farm → 2. Device Remote Control → 2-7 ADB Proxy Guide.
3.  Push a sample image for testing to the following path using adb push.
4.  After confirming a proper connection with Device Farm, select Tools -> Device Manager in Android Studio.
5.  Next that check if the physical device is properly connected.
6.  Run the Object Detection project from the Sample Applications obtained via git clone in Android Studio.
7.  Upload the image data for inference and execute the application.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()`, `convertBitmapToFloatArray()` functions.

***

### Depth Estimation In Android
This Application explains how a simple Android Sample Application operates using the [MiDaS v2](https://prd.ai-studio-farm.com/kr/solution/ai/models/detail/73bcae23-4a07-4df9-b0c5-80504427c11c) model optimized for Exynos hardware.

#### Functionality
This Application receives input from an image file or a camera. 
A color representing the estimated distance is overlaid on each pixel, providing a visual representation of depth. 
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
5.  Next that check if the physical device is properly connected.
6.  Run the Object Detection project from the Sample Applications obtained via git clone in Android Studio.
7.  Upload the image data for inference and execute the application.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()`, `convertBitmapToFloatArray()` functions.

***

## Native Samples
This section provides an overview of the native sample program.
Each sample program entry provides the functionality of the sample program, its location, and instructions for running it.

***