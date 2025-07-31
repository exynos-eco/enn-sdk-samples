# Segmentation In Android
This document describes a method to operate Android sample application operates using the [DDRNet23_slim](https://soc-developer.semiconductor.samsung.com/kr/solution/ai/models/detail/994cb06f-b886-4fb6-b8e9-8b4efdc8baee) model that is optimized for Exynos hardware.

## Functionality
This sample application provides semantic segmentation results for images that are either from stored image files or those captured through the camera.
Each pixel of the segmented object is overlaid with a color corresponding to its label, thereby offering a visual representation of the classification.

<p align="center" width="100%">
  <img src="semantic-segmentation.png" alt="App Classification UI" height="400"/>
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
[ESPNet](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/d1cc15da-7239-4b84-834a-19fbd632c4fa)  
[FCN_ResNet50](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/4f98c13b-560f-4b24-b938-dac70cbd3239)  
[UNet_Brain_Segmentation](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/04656d4c-2814-4d11-919e-e3d806bdda83)  