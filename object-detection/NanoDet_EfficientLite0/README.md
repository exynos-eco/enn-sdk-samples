# Object Detection In Android
This document describes a method to operate Android sample application using the [NanoDet_EfficientLite0](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/NanoDet_EfficientLite0) model that is optimized for Exynos hardware.

## Functionality
This sample application identifies objects in images that are either from stored image files or those captured through the camera.
The detected objects are highlighted with bounding boxes, and the label and score of each object are displayed.
Additionally, the inference time is displayed at the bottom of the application interface.

<p align="center" width="100%">
  <img src="NanoDet_EfficientLite0.png" alt="App Classification UI" height="400"/>
</p>

## Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the AI Studio Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the object detection project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

## Compatible AI Models
Below is a list of models expected to be compatible with the sample application.  
**Note:** All models that are listed here are not individually tested with this application.  
[CenterNet_Detection](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/CenterNet_Detection?tab=float&chipset=Exynos+2600)  
[DETR_ResNet101_dc5](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/DETR_ResNet101_dc5)  
[FoveaBox_R50_1x](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/FoveaBox_R50_1x?tab=float&chipset=Exynos+2600)  
[FoveaBox_R50_2x](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/FoveaBox_R50_2x?tab=float&chipset=Exynos+2600)  
[FoveaBox_R101_1x](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/FoveaBox_R101_1x?tab=float&chipset=Exynos+2600)  
[FoveaBox_R101_2x](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/FoveaBox_R101_2x?tab=float&chipset=Exynos+2600)  
[YOLOX_l](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/YOLOX_l)  
[YOLOX_m](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/YOLOX_m)  
[YOLOX_s](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/YOLOX_s)  
[YOLOX_tiny](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/YOLOX_tiny)  
[YOLOX_nano](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/YOLOX_nano)  
[NanoDet_EfficientLite1](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/NanoDet_EfficientLite1)  
[NanoDet_EfficientLite2](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/NanoDet_EfficientLite2)  
