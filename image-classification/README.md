# Image Classification In Android
This document describes a method to operate Android sample application using the [DenseNet121](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/118f8cc6-f251-43b7-b8c2-ec77a3c50fda) model that is optimized for Exynos hardware.

## Functionality
This application classifies objects in images that are either from stored image files or those captured through a camera.
The classified items, corresponding scores, and inference time are displayed at the bottom of the application interface.

<p align="center" width="100%">
  <img src="image-classification.png" alt="App Classification UI" height="400"/>
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
[EfficientNet_B4](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/9d310aaa-d2f0-43d8-bdb1-0c31413da46e)  
[MobileNet_v2](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/0c031a1e-0eed-442d-9691-421d416a5556)  
[ResNet18](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/4c29e543-f74f-4bc3-a373-bc993c7ac7df)  
[ResNet34_v1_7](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/df74a1bf-b048-4648-9396-31231b6fed49)  
[ResNet50](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/27b58ffc-c760-4c87-ab60-533aba27ffa6)  
[ResNet101](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/311c216e-f50c-4fee-a400-952b1fb96506)  
[SqueezeNet1_1](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/546abf23-be6c-4a1a-9d65-edb48e94eb3a)  
[EfficientNet_B0](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/21ed28ef-d958-4cec-8d29-2d13efaf0468)  
[MNASNet05](https://prd.ai-studio-farm.com/global/solution/ai/models/detail/34efd7b3-8f3d-44fa-9440-34365277ff5f)  
[MobileNet_v2](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/0c031a1e-0eed-442d-9691-421d416a5556)  
[ResNet18](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/4c29e543-f74f-4bc3-a373-bc993c7ac7df)  
[ResNet34_v1_7](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/df74a1bf-b048-4648-9396-31231b6fed49)  
[ResNet50](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/27b58ffc-c760-4c87-ab60-533aba27ffa6)  
[ResNet101](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/311c216e-f50c-4fee-a400-952b1fb96506)  
[ResNet152](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/d9766645-99b0-48c6-b3bb-c22e2efda2fd)  
[SqueezeNet1_1](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/546abf23-be6c-4a1a-9d65-edb48e94eb3a)  
[AlexNet](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/0c40065c-31aa-4e55-a05b-0133728e4b29)  
[ConvNext_Base](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/f46d37b1-4164-41cc-b63f-bdd649fc1f75)  
[ConvNext_Small](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/2e3a300a-e53f-45b4-9218-ac068bae93bc)  
[ConvNext_Tiny](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/b081991e-9b82-4b3d-9bee-68146cdd6ecf)  
[DenseNet161](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/6e15d271-2da7-4450-9cdd-00dd17daf49c)  
[DenseNet169](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/b8e37ace-5dd1-497e-ac49-92d0540ec4b8)  
[DenseNet201](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/1d427318-1b24-496e-8657-67428f95eb41)  
[DLA34](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/159e1e8e-a33b-44f6-800b-1524d8955d32)  
[DLA60](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/335fa7e1-eb99-450a-8c46-5841d0c76398)  
[EfficientNet_B0](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/21ed28ef-d958-4cec-8d29-2d13efaf0468)  
[EfficientNet_B1](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/4d70b32d-6523-412d-9aed-43b2b13679ef)  
[EfficientNet_B2](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/050f1692-9e42-42ba-9c54-c1d5af8dce0a)  
[EfficientNet_B3](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/305a291e-6632-4e7c-ba27-8e21756ab9e1)  
[EfficientNet_B4](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/9d310aaa-d2f0-43d8-bdb1-0c31413da46e)  
[EfficientNet_B5](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/5fc22d0d-5039-4291-b4be-86dd3e6382da)  
[EfficientNet_B6](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/0069613b-8579-4959-9580-91fa4db61c7b)  
[EfficientNet_v2_l](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/6bec58a0-a86d-4aef-9656-542c5ad07995)  
[EfficientNet_v2_m](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/006ae2ec-f59f-49ce-bd23-b94924a661ef)  
[EfficientNet_v2_s](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/8f636baf-0c36-4651-865e-cc0c1dcb1d3b)  
[GoogleNet](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/970da037-bf08-4595-a5f0-b527c88a2d47)  
[Inception_v3](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/dae24391-a31a-4091-8815-7861f9f476ea)  
[Inception_v4](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/08f3840f-18fb-474c-ad0c-eca62ab6b521)  
[MNASNet05](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/34efd7b3-8f3d-44fa-9440-34365277ff5f)  
[MNASNet0_75](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/d2e0eb6b-b686-4bf5-8c5e-76ba4acaf22b)  
[MNASNet1_0](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/13bd3ace-21b8-4fa2-a7ea-05c766ce9593)  
[MNASNet1_3](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/4ec61154-05cd-4172-b0d3-c7bed1c598f1)  
[RegNetX_200MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/f83be596-0c6e-4d30-b575-0f9673511705)  
[RegNetX_400MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/9cf3d274-17e7-4dbc-935c-030d6cf901a1)  
[RegNetX_600MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/aa230396-2a8f-4cdb-a518-0b45ef6eb6dd)  
[RegNetX_800MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/8ec5daec-b082-4f11-b079-d1f80f6dfb3c)  
[RegNetX_1.6GF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/085240cf-f9d1-4c1e-b475-cf8a6395308f)  
[RegNetY_200MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/c4555f09-e9d9-49bd-a5f9-313413b2331e)  
[RegNetY_400MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/fb3b92c3-d7b7-4bc8-83b4-077025bb50fe)  
[RegNetY_600MF](https://soc-developer.semiconductor.samsung.com/global/solution/ai/models/detail/a04ecde1-7ded-4dc0-9668-6b19ca0d53c7)  
