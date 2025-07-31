# Object Detection In Android
This document describes a method to operate Android sample application using the [DETR_ResNet50_dc5](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/a631921e-dc8b-46cb-ac17-d23c5a54db26) model that is optimized for Exynos hardware.

## Functionality
This sample application identifies objects in images that are either from stored image files or those captured through the camera.
The detected objects are highlighted with bounding boxes, and the label and score of each object are displayed.
Additionally, the inference time is displayed at the bottom of the application interface.

<p align="center" width="100%">
  <img src="object-detection.png" alt="App Classification UI" height="400"/>
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
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

## Compatible AI Models
Below is a list of models expected to be compatible with the sample application.  
**Note:** All models that are listed here are not individually tested with this application.  
[DETR_ResNet101_dc5](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/9eb3e0fd-4478-49e4-b631-5941ce62d16c)  
[YOLOX_l](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/82ac3ced-738f-419e-a37c-fbab1fd6c3ae)  
[YOLOX_m](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/7d44174f-e815-48c3-8ef1-4f7c8acac522)  
[YOLOX_s](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/89bd4a9e-ffa4-420a-a9d7-25f16b7942f1)  
[YOLOX_tiny](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/e9f2d207-0eaa-468b-bdd1-c67333545404)  
[YOLOX_nano](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/97a88b8e-f9ab-44e3-a145-20cfe90cae41)  
