package hu.rezsekmv.cameracontroller.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CameraControlViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraControlViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
