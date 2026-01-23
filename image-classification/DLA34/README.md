# Image Classification In Android
This document describes a method to operate Android sample application using the [DLA34](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/DLA34) model that is optimized for Exynos hardware.

## Functionality
This application classifies objects in images that are either from stored image files or those captured through a camera.
The classified items, corresponding scores, and inference time are displayed at the bottom of the application interface.

<p align="center" width="100%">
  <img src="DLA34.png" alt="App Classification UI" height="400"/>
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
[MobileNet_v2](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/MobileNet_v2)  
[ResNet18](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/ResNet18)  
[ResNet34_v1_7](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/ResNet34_v1_7)    
[ResNet50](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/ResNet50)  
[ResNet101](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/ResNet101)  
[ResNet152](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/ResNet152)  
[SqueezeNet1_1](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/SqueezeNet1_1)  
[AlexNet](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/AlexNet)  
[ConvNext_Base](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/ConvNext_Base)  
[ConvNext_Small](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/ConvNext_Small)    
[ConvNext_Tiny](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/ConvNext_Tiny)  
[DenseNet121](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/DenseNet121)  
[DenseNet161](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/DenseNet161)  
[DenseNet169](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/DenseNet169)  
[DenseNet201](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/DenseNet201)   
[DLA60](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/DLA60)  
[EfficientNet_B0](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_B0)    
[EfficientNet_B1](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_B1)  
[EfficientNet_B2](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_B2)  
[EfficientNet_B3](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_B3)  
[EfficientNet_B4](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_B4)  
[EfficientNet_B5](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_B5)  
[EfficientNet_B6](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_B6)  
[EfficientNet_v2_l](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_v2_l)    
[EfficientNet_v2_m](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_v2_m)  
[EfficientNet_v2_s](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/EfficientNet_v2_s)  
[GoogleNet](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/GoogleNet)  
[HGNet_Base](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNet_Base)  
[HGNet_Small](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNet_Small)  
[HGNet_Tiny](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNet_Tiny)  
[HGNetv2_B0](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNetv2_B0)  
[HGNetv2_B1](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNetv2_B1)  
[HGNetv2_B2](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNetv2_B2)  
[HGNetv2_B3](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNetv2_B3)  
[HGNetv2_B4](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNetv2_B4)  
[HGNetv2_B5](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/HGNetv2_B5)  
[Inception_v3](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Inception_v3)  
[Inception_v4](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Inception_v4)  
[Legacy_SeNet154](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Legacy_SeNet154)  
[Legacy_SeResNet18](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Legacy_SeResNet18)  
[Legacy_SeResNet34](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Legacy_SeResNet34)  
[Legacy_SeResNet50](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Legacy_SeResNet50)  
[Legacy_SeResNet101](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Legacy_SeResNet101)  
[Legacy_SeResNet152](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/Legacy_SeResNet152)  
[MNASNet05](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/MNASNet05)  
[MNASNet0_75](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/MNASNet0_75)  
[MNASNet1_0](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/MNASNet1_0)  
[MNASNet1_3](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/MNASNet1_3)  
[RegNetX_200MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/RegNetX_200MF)  
[RegNetX_400MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/RegNetX_400MF)  
[RegNetX_600MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/RegNetX_600MF)  
[RegNetX_800MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/RegNetX_800MF)  
[RegNetX_1.6GF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/RegNetX_1.6GF)  
[RegNetY_200MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/RegNetY_200MF)  
[RegNetY_400MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/RegNetY_400MF)  
[RegNetY_600MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/RegNetY_600MF)  
[StarNet_s1](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/StarNet_s1)  
[StarNet_s2](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/StarNet_s2)  
[StarNet_s3](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/StarNet_s3)  
[StarNet_s4](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/StarNet_s4)  
[VGG11](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/VGG11)  
[VGG13](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/VGG13)  
[VGG16](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/VGG16)  
[VGG19](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/VGG19)  
