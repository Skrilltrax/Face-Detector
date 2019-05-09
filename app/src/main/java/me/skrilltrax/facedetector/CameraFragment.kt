package me.skrilltrax.facedetector

import android.app.Activity
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class CameraFragment : Fragment(), TextureView.SurfaceTextureListener, Callbacks {

    private lateinit var texture: TextureView
    private lateinit var faceText: TextView
    private lateinit var camera: Camera
    private val callbacks = this

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findViews(view)
        texture.surfaceTextureListener = this
        faceText.setOnClickListener{
            faceText.visibility = View.GONE
        }
        camera = Camera(
            (this@CameraFragment.activity as Activity),
            this@CameraFragment.context!!,
            activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager,
            texture,
            callbacks
        )
    }

    private fun findViews(view: View) {
        texture = view.findViewById(R.id.texture)
        faceText = view.findViewById(R.id.face_found)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        camera.openCamera()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {

    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        return false
    }

    override fun onResume() {
        super.onResume()
        camera.openBackgroundThread()
        if (texture.isAvailable) {
            camera.setupCamera()
            camera.openCamera()
        } else {
            texture.surfaceTextureListener = this
        }
    }

    override fun onStop() {
        camera.closeCamera()
        camera.closeBackgroundThread()
        super.onStop()
    }

    override fun faceFound() {
        faceText.visibility = View.VISIBLE
    }

    override fun faceNotFound() {
        faceText.visibility = View.GONE
    }

    companion object {
        fun newInstance(): CameraFragment {
            return CameraFragment()
        }
    }
}
