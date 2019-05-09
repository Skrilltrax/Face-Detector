package me.skrilltrax.facedetector

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val CAMERA_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)
        if (supportFragmentManager.backStackEntryCount == 0) {
            supportFragmentManager.beginTransaction()
                .add(R.id.camera_frame, CameraFragment.newInstance(), CameraFragment::class.java.simpleName)
                .commit()
        }
        val permissions: Array<String> = Array(1) {android.Manifest.permission.CAMERA}
        ActivityCompat.requestPermissions(this, permissions, CAMERA_REQUEST_CODE)
    }
}
