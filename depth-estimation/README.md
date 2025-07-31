# Depth Estimation In Android
This document describes a method to operate Android sample application operates using the [MiDaS_v2](https://soc-developer.semiconductor.samsung.com/kr/solution/ai/models/detail/73bcae23-4a07-4df9-b0c5-80504427c11c) model that is optimized for Exynos hardware.

## Functionality
This application receives input that are either from an image file or those captured through a camera.
A color representing the estimated distance is overlaid on each pixel that, provides a visual representation of depth.
Additionally, the inference time is displayed at the bottom of the application interface.

<p align="center" width="100%">
  <img src="depth-estimation.png" alt="App Classification UI" height="400"/>
</p>

## Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the AI Studio Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the depth estimation project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

## Compatible AI Models
Currently there are no supported models.  
**Note:** All models that are listed here are not individually tested with this application.  
[MegaDepth](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/b3b19a15-1d46-4e83-adbd-8ebaa5522e5c)  
