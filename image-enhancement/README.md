# Image Enhance In Android
This Application explains how a simple Android Sample Application operates using the [Real_ESRGAN_General_x4v3](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/36ad7134-5621-48b2-8ddf-e4889417f6ef) model optimized for Exynos hardware.

## Functionality
This Application enhances the low-light quality of images from stored image files or those captured via the camera.
Additionally, the inference time is displayed at the bottom of the application interface.

## Getting Started
To utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no available device to run the application, you can use the real devices provided in the Device Farm.
    For guidance on connecting a device to Android Studio, refer to the [Documentation](https://prd.ai-studio-farm.com/global/development/enn-sdk/document/documentation) menu under AI Studio Farm → 2. Device Remote Control → 2-7 ADB Proxy Guide.
3.  Push a sample image for testing to the following path using adb push.
4.  After confirming a proper connection with Device Farm, select Tools -> Device Manager in Android Studio.
5.  Next that check if the physical device is properly connected.
6.  Run the Object Detection project from the Sample Applications obtained via git clone in Android Studio.
7.  Upload the image data for inference and execute the application.

To modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
3.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()`, `convertBitmapToFloatArray()` functions.

## Compatible AI Models
[Esrgan](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/6ba633da-e9c2-4264-bf4a-9a21eba95e46)
