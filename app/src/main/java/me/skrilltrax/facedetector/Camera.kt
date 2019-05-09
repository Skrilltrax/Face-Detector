package me.skrilltrax.facedetector

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.HandlerThread
import android.util.SparseIntArray
import android.view.Surface
import androidx.core.app.ActivityCompat
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.TextureView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class Camera(
    private val activity: Activity,
    private val context: Context,
    private val manager: CameraManager,
    private val texture: TextureView,
    private val callbacks: Callbacks
) {

    private val cameraFacing: Int
    private val ORIENTATIONS: SparseIntArray = SparseIntArray()
    private var cameraId: String
    private var cameraDevice: CameraDevice? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: Thread? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var previewSize: Size
    private lateinit var stateCallback: CameraDevice.StateCallback
    private lateinit var cameraSessionStateCallback: CameraCaptureSession.StateCallback
    private lateinit var onImageAvailableReader: ImageReader.OnImageAvailableListener

    init {
        cameraId = ""

        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)

        cameraFacing = CameraCharacteristics.LENS_FACING_FRONT
        setupCallbacks()
        setupCamera()
        openBackgroundThread()
    }

    fun setupCamera() {
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == cameraFacing) {
                    val streamConfigurationMap =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    if (streamConfigurationMap != null) {
                        previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture::class.java)[0]
                        this.cameraId = cameraId
                    }
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    fun openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                manager.openCamera(cameraId, stateCallback, backgroundHandler)
                imageReader = ImageReader.newInstance(480, 360, ImageFormat.YUV_420_888, 5)
                imageReader!!.setOnImageAvailableListener(onImageAvailableReader, backgroundHandler)
            }

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun createPreviewSession() {
        try {
            val surfaceTexture = texture.surfaceTexture
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(surfaceTexture)
            if (cameraDevice != null) {
                captureRequestBuilder =
                    (cameraDevice as CameraDevice).createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder.addTarget(previewSurface)
                if (imageReader != null) {
                    captureRequestBuilder.addTarget(imageReader?.surface!!)
                }
                if (cameraDevice != null && imageReader != null) {
                    cameraDevice!!.createCaptureSession(
                        listOf(previewSurface, imageReader?.surface),
                        cameraSessionStateCallback,
                        backgroundHandler
                    )
                }
            }

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun openBackgroundThread() {
        backgroundThread = HandlerThread("camera_thread")
        backgroundThread?.start()
        backgroundHandler = Handler((backgroundThread as HandlerThread).looper)
    }

    fun closeCamera() {
        if (cameraCaptureSession != null) {
            (cameraCaptureSession as CameraCaptureSession).close()
            cameraCaptureSession = null
        }

        if (cameraDevice != null) {
            (cameraDevice as CameraDevice).close()
            cameraDevice = null
        }

        if (imageReader != null) {
            imageReader!!.close()
            imageReader = null
        }
    }

    fun closeBackgroundThread() {
        if (backgroundHandler != null) {
            (backgroundThread as HandlerThread).quitSafely()
            backgroundThread = null
            backgroundHandler = null
        }
    }


    private fun setupCallbacks() {
        stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createPreviewSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraDevice?.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                cameraDevice?.close()
                cameraDevice = null
            }
        }

        cameraSessionStateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (cameraDevice == null) return
                try {
                    val captureRequest = captureRequestBuilder.build()
                    cameraCaptureSession = session
                    cameraCaptureSession!!.setRepeatingRequest(captureRequest, null, backgroundHandler)
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
            }
        }

        onImageAvailableReader = ImageReader.OnImageAvailableListener {
            createOnImageAvailableObservable(it)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.newThread())
                .subscribe(
                    { reader ->
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            FaceDetector.detectImage(image, cameraId, activity, context, ORIENTATIONS, callbacks)
                            image.close()
                        }
                    }, {

                    }
                )
            /*it.apply {
                FaceDetector.detectImage(this.acquireLatestImage(), cameraId, activity, context, ORIENTATIONS)
                it.acquireLatestImage().close()
            }*/
            /*backgroundHandler?.post(Runnable {
                val image = it.acquireLatestImage()
                if (image != null) {
                    FaceDetector.detectImage(image, cameraId, activity, context, ORIENTATIONS)
                    image.close()
                }
            })*/
        }

    }

    private fun createOnImageAvailableObservable(imageReader: ImageReader): Observable<ImageReader> {
        Log.d("Camera", "createOnImageAvailableReaderOut")
        return Observable.create { subscriber ->
            val listener = { imageReader: ImageReader ->
                if (!subscriber.isDisposed) {
                    subscriber.onNext(imageReader)
                }
            }
            imageReader.setOnImageAvailableListener(listener, backgroundHandler)
            Log.d("Camera", "createOnImageAvailableReader")
            subscriber.setCancellable {
                imageReader.setOnImageAvailableListener(
                    null,
                    backgroundHandler
                )
            }
        }
    }

}
