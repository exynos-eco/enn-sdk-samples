# Image Enhance In Android
This document describes a method to operate Android sample application using the [Real-ESRGAN-General-x4v3](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/36ad7134-5621-48b2-8ddf-e4889417f6ef) model that is optimized for Exynos hardware.

## Functionality
This application enhances the low-light quality of images that are either from stored image files or those captured through the camera. 
Additionally, the inference time is displayed at the bottom of the application interface.

## Getting Started
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

## Compatible AI Models
Below is a list of models expected to be compatible with the sample application.
**Note:** All models that are listed here are not individually tested with this application.  
[ESRGAN](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/6ba633da-e9c2-4264-bf4a-9a21eba95e46)
