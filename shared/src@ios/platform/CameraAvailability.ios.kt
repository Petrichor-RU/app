package nl.petrichor.app.platform

import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerSourceType

actual fun isCameraAvailable(): Boolean =
    UIImagePickerController.isSourceTypeAvailable(
        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    )
