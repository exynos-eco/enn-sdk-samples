# Video Classification In Android
This document describes a method to operate Android sample application using the [TSN_ResNet50]() model that is optimized for Exynos hardware.

## Functionality
This application classifies actions in videos that are selected from stored video files.
The classified items, corresponding scores, and inference time are displayed at the bottom of the application interface.

<p width="100%">
  <img src="video-classification.png" alt="App Classification UI" height="400" />
</p>

## Getting Started
Perform the following steps to utilize the sample application:
1.	Download or clone the sample application from this repository.
2.  If there is no device available to run the application, you can use the actual devices provided in the Device Farm.
    For more information on connecting a device to Android Studio, refer to ADB Client Proxy.
3.  Use adb push command to push a sample video to the following path for testing.
4.  Select Tools → Device Manager in Android Studio. Please verify whether the physical device is properly connected.
5.  Run the video classification project from the sample applications obtained through git clone in Android Studio.
6.  Upload the video data for inference and execute the application.

Perform the following steps to modify the model used in the sample application:
1.	Copy the desired model file to the `assets` directory of the project.
2.	Copy the corresponding label text file to the `assets` directory.
3.	Modify the parameters in the ModelConstants.kt file to reflect the specifications of the new model.
4.	If the inputs and outputs of the model differ from the pre-designed sample application, modify the `preProcess()`, `postProcess()` and `convertBitmapToFloatArray()` functions.

## Compatible AI Models
Below is a list of models expected to be compatible with the sample application.  
**Note:** All models that are listed here are not individually tested with this application.

