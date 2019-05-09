package me.skrilltrax.facedetector

import android.app.Activity
import android.content.Context
import android.content.Context.CAMERA_SERVICE
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import android.util.Log
import android.util.SparseIntArray
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions

class FaceDetector {


    companion object {

        val realTimeOpts = FirebaseVisionFaceDetectorOptions.Builder()
            .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
            .build()

        fun detectImage(
            image: Image,
            cameraId: String,
            activity: Activity,
            context: Context,
            ORIENTATIONS: SparseIntArray,
            callbacks: Callbacks
        ) {
            val orientation = getRotationCompensation(cameraId, activity, context, ORIENTATIONS)
            val firebaseImage = FirebaseVisionImage.fromMediaImage(image,orientation)
            val detector = FirebaseVision.getInstance().getVisionFaceDetector(realTimeOpts)

            detector.detectInImage(firebaseImage)
                .addOnSuccessListener {faces ->
                    if (!faces.isEmpty()) {
                        callbacks.faceFound()
                    }
                }
                .addOnFailureListener {
                }
        }

        private fun getRotationCompensation(cameraId: String, activity: Activity, context: Context, ORIENTATIONS: SparseIntArray): Int {

            val deviceRotation = activity.windowManager.defaultDisplay.rotation
            var rotationCompensation = ORIENTATIONS.get(deviceRotation)

            val cameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager
            val sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION)!!
            rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360

            val result: Int
            when (rotationCompensation) {
                0 -> result = FirebaseVisionImageMetadata.ROTATION_0
                90 -> result = FirebaseVisionImageMetadata.ROTATION_90
                180 -> result = FirebaseVisionImageMetadata.ROTATION_180
                270 -> result = FirebaseVisionImageMetadata.ROTATION_270
                else -> {
                    result = FirebaseVisionImageMetadata.ROTATION_0
                    Log.e("Firebase", "Bad rotation value: $rotationCompensation")
                }
            }
            return result
        }
    }

}