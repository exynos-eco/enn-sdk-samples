# Gaze Estimation In Android
This document describes a method to operate Android sample application using the [Gaze_MobileNetV2](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Gaze_MobileNetv2?tab=float&chipset=Exynos+2600) model that is optimized for Exynos hardware.

## Functionality
The application takes input from an image file or a live camera stream. The model analyzes the face and eye regions to estimate the gaze direction of the person.
The predicted gaze direction is visualized on the screen, and the inference time is displayed at the bottom of the application interface.

<p align="center" width="100%">
  <img src="Gaze_MobileNetV2.png" alt="App Classification UI" height="400"/>
</p>

## Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the AI Studio Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample image to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the gaze estimation project from the sample applications obtained through git clone in Android Studio.
6.  Upload the image data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

## Compatible AI Models
Below is a list of models expected to be compatible with the sample application.  
**Note:** All models that are listed here are not individually tested with this application.  
[Gaze_ResNet18](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Gaze_ResNet18?tab=float&chipset=Exynos+2600)  
[Gaze_ResNet34](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Gaze_ResNet34?tab=float&chipset=Exynos+2600)  
[Gaze_ResNet50](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Gaze_ResNet50?tab=float&chipset=Exynos+2600)    
[L2CSNet](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/L2CSNet?tab=float&chipset=Exynos+2600)  


