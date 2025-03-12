# Object Detection In Android
This Application explains how a simple Android Sample Application operates using the [Detr_resnet50_dc5](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/a631921e-dc8b-46cb-ac17-d23c5a54db26) model optimized for Exynos hardware.

## Functionality
This Sample Application identifies objects in images either from stored image files or input via the camera.
Detected objects are highlighted with bounding boxes, and the label and score of each object are displayed.
Additionally, the inference time is shown at the bottom of the application interface.

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
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()` and `postProcess()`, `convertBitmapToFloatArray()` functions.

## Compatible AI Models
[Detr_resnet101_dc5](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/9eb3e0fd-4478-49e4-b631-5941ce62d16c)