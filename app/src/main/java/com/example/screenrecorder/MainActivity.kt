package com.example.screenrecorder

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import android.widget.VideoView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.lang.Exception
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE = 1000
    private val REQUEST_PERMISSION = 1001
    private lateinit var mediaProjectionManeger : MediaProjectionManager
    private var mediaProjection : MediaProjection ?= null
    private var virtualDisplay : VirtualDisplay ?= null
    private lateinit var mediaProjectionCallBack : MediaProjectionCallBack

    private var mScreenDensity : Int ?=null
    private var DISPLAY_WIDTH = 720
    private var DISPLAY_HEIGHT = 1280

    private var mediaRecorder : MediaRecorder?=null
    private lateinit var toggleBtn : FloatingActionButton

    var isChecked = false

    private lateinit var videoView : VideoView
    private var videoUrl : String = ""
    private val ORIENTATION = SparseIntArray()

    init {
        ORIENTATION.append(Surface.ROTATION_0,90)
        ORIENTATION.append(Surface.ROTATION_90,0)
        ORIENTATION.append(Surface.ROTATION_180,270)
        ORIENTATION.append(Surface.ROTATION_270,180)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        mediaRecorder = MediaRecorder()
        mediaProjectionManeger = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        videoView = findViewById(R.id.videoView)
        toggleBtn = findViewById(R.id.toggleBtn)

        toggleBtn.setOnClickListener{
            if (
                ContextCompat.checkSelfPermission(
                    this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) + ContextCompat.checkSelfPermission(
                    this,android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ){
                isChecked = false
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.RECORD_AUDIO
                    ),REQUEST_PERMISSION
                )
            }
            else {
                toggleScreenShare(toggleBtn)
            }
        }
    }

    private fun toggleScreenShare(v: FloatingActionButton?) {
        if (!isChecked){
            initRecorder()
            recorderScreen()
            isChecked = true
            toggleBtn.setImageResource(R.drawable.ic_stop)
        }
        else{
            try {
                mediaRecorder!!.stop()
                mediaRecorder!!.reset()
                stopRecordingScreen()
            }
            catch (e : Exception){
                e.printStackTrace()
            }

            videoView.visibility = View.VISIBLE
            videoView.setVideoURI(Uri.parse(videoUrl))
            videoView.start()
            isChecked = false
            toggleBtn.setImageResource(R.drawable.ic_video)
        }
    }

    private fun stopRecordingScreen() {
        if (virtualDisplay == null){
            return
            virtualDisplay!!.release()
            destroMediaProjection()
        }
    }

    private fun destroMediaProjection() {
        if (mediaProjection == null){
            mediaProjection!!.unregisterCallback(mediaProjectionCallBack)
            mediaProjection!!.stop()
            mediaProjection = null
        }
    }

    private fun recorderScreen() {
        if (mediaProjection == null){
            startActivityForResult(mediaProjectionManeger.createScreenCaptureIntent(),REQUEST_CODE)
        }
        virtualDisplay = createVirtualDisplay()

        try {
            mediaRecorder!!.start()
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        return mediaProjection!!.createVirtualDisplay(
            "MainActivity",DISPLAY_WIDTH,DISPLAY_HEIGHT,
            mScreenDensity!!,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface,null,null
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE){
            Toast.makeText(this,"Error",Toast.LENGTH_SHORT).show()
            return
        }
        if (resultCode != RESULT_OK){
            Toast.makeText(this,"Permission denied",Toast.LENGTH_SHORT).show()
            isChecked = false
            return
        }
        mediaProjectionCallBack = MediaProjectionCallBack(
            mediaRecorder!!,mediaProjection
        )
        mediaProjection = mediaProjectionManeger.getMediaProjection(
            resultCode,data!!
        )
        mediaProjection!!.registerCallback(mediaProjectionCallBack,null)
        virtualDisplay = createVirtualDisplay()
        try {
            mediaRecorder!!.start()
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    private fun initRecorder() {
        try {
            var recordingFile = ("ScreenRes${System.currentTimeMillis()}.mp4")
            mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

            val newPath  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val folder = File(newPath,"MyScreenRec/")
            if (folder.exists()){
                folder.mkdir()
            }
            val file1 = File(folder,recordingFile)
            videoUrl = file1.absolutePath

            mediaRecorder!!.setOutputFile(videoUrl)
            mediaRecorder!!.setVideoSize(DISPLAY_HEIGHT,DISPLAY_WIDTH)
            mediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mediaRecorder!!.setVideoEncodingBitRate(512*1000)
            mediaRecorder!!.setVideoFrameRate(30)

            var rotation = windowManager.defaultDisplay.rotation
            var orientation = ORIENTATION.get(rotation +90)

            mediaRecorder!!.setOrientationHint(orientation)
            mediaRecorder!!.prepare()
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

    inner class MediaProjectionCallBack(
        var mediaRecord : MediaRecorder,
        var mediaProjection:MediaProjection?
    ):MediaProjection.Callback(){
        override fun onStop() {
            if (isChecked){
                isChecked = false
                mediaRecord.stop()
                mediaRecord.reset()
            }
            mediaProjection = null
            stopRecordingScreen()
            super.onStop()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            REQUEST_CODE ->{
                if(grantResults.size >0 &&  grantResults[0]+grantResults[1]==PackageManager.PERMISSION_GRANTED){
                    toggleScreenShare(toggleBtn)
                }
            else{
                isChecked = false
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.RECORD_AUDIO
                ),REQUEST_PERMISSION
            )}
            }
        }
    }
}