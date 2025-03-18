# Image Classification In Android
This document describes a method to operate Android sample application using the [DenseNet121](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/118f8cc6-f251-43b7-b8c2-ec77a3c50fda) model that is optimized for Exynos hardware.

## Functionality
This application classifies objects in images that are either from stored image files or those captured through a camera.
The classified items, corresponding scores, and inference time are displayed at the bottom of the application interface.

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
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

## Compatible AI Models
Below is a list of models expected to be compatible with the sample application.
**Note:** All models that are listed here are not individually tested with this application.
[EfficientNet_B4](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/9d310aaa-d2f0-43d8-bdb1-0c31413da46e)
[MobileNet_v2](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/0c031a1e-0eed-442d-9691-421d416a5556)
[ResNet18](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/4c29e543-f74f-4bc3-a373-bc993c7ac7df)
[ResNet34_v1_7](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/df74a1bf-b048-4648-9396-31231b6fed49)
[ResNet50](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/27b58ffc-c760-4c87-ab60-533aba27ffa6)
[ResNet101](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/311c216e-f50c-4fee-a400-952b1fb96506)
[SqueezeNet1_1](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/546abf23-be6c-4a1a-9d65-edb48e94eb3a)
[EfficientNet_B0](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/21ed28ef-d958-4cec-8d29-2d13efaf0468)
[MNASNet05](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/34efd7b3-8f3d-44fa-9440-34365277ff5f)
