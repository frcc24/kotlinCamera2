package br.com.cameraapi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.view.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import br.com.cameraapi.databinding.FragmentFirstBinding
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.lang.Exception
import java.lang.IllegalStateException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


const val REQUEST_CAMERA_PERMISSION = 100

class FirstFragment : Fragment() {

    private val MAX_PREVIEW_WIDTH = 1000
    private val MAX_PREVIEW_HEIGHT = 1000
    private var isRecording = false
    val mediaRecorder by lazy {  MediaRecorder() }
    private lateinit var currentVideoFilePath: String


    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder

    private lateinit var cameraDevice: CameraDevice
    private val deviceStateCall = object: CameraDevice.StateCallback(){
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onOpened(camera: CameraDevice) {
            if (camera != null){
                cameraDevice = camera
                Log.d("tag", "camera device oppened")
                previewSession()
            }
        }
        override fun onDisconnected(camera: CameraDevice) {
            camera?.close()
        }
        override fun onError(camera: CameraDevice, p1: Int) {
            Log.d("tag", "camera device error")
            this@FirstFragment.activity?.finish()
        }
    }
    private lateinit var backgroudThread: HandlerThread
    private lateinit var backgroudHandler: Handler


    private val cameraManager by lazy {
        activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private fun createFileName(): String{
        val timestamp = SimpleDateFormat( "ddMMyyyy_HHmm").format(Date())
        return "Video_${timestamp}.mp4"
    }

    private fun createVideoFile(): File {
        val videoFile = File(context?.filesDir, createFileName())
        currentVideoFilePath = videoFile.absolutePath
        return videoFile
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setupMediaRecorder( ) {
        mediaRecorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(createVideoFile())
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(1000,1000)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            prepare()
        }
        //mediaRecorder.prepare()
    }
    fun stopMediaRecorder(){
        mediaRecorder.apply {
            try {
                stop()
                reset()
            }catch (e: Exception){
                Log.d("tag", e.toString())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun previewSession(){

        setupMediaRecorder()

        val surfaceTexture = binding.cameraView.surfaceTexture
        if (surfaceTexture != null) {
            surfaceTexture.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
            val surface = Surface(surfaceTexture)
            val recordingSurface = mediaRecorder.surface

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            captureRequestBuilder.addTarget(surface)
            captureRequestBuilder.addTarget(recordingSurface)

            val surfaces = ArrayList<Surface>().apply {
                add(surface)
                add(recordingSurface)
            }

            cameraDevice.createCaptureSession(surfaces, object: CameraCaptureSession.StateCallback(){
                override fun onConfigured(session: CameraCaptureSession) {
                    if( session != null){
                        captureSession = session
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                        isRecording = true
                        mediaRecorder.start()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.d("tag", "creatig capture session failed: ${session.device.id}")
                }

            }, backgroudHandler)
        }
    }

    private fun closeCamera(){
        if(this::captureSession.isInitialized){
            captureSession.close()
        }
        if (this::cameraDevice.isInitialized){
            cameraDevice.close()
        }
    }

    private fun startBackgroundThread(){
        backgroudThread = HandlerThread("Camera2 Kotlin").also { it.start() }
        backgroudHandler = Handler(backgroudThread.looper)
    }

    private fun stopBackgroundThread(){
        backgroudThread.quitSafely()
        try {
            backgroudThread.join()
        }catch (e: InterruptedException){
            Log.d("tag", e.toString())
        }
    }

    private fun <T> cameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>): T? {

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return when (key) {
            //CameraCharacteristics.LENS_FACING -> characteristics.get(key)
            CameraCharacteristics.LENS_FACING -> characteristics.get(key)
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP -> characteristics.get(key)

            else -> throw IllegalArgumentException("Key not recongnized")
        }
    }

    private fun cameraId(lens: Int) : String{
        var deviceId = listOf<String>()
        try {
            val cameraIdList = cameraManager.cameraIdList
            deviceId = cameraIdList.filter { lens == cameraCharacteristics(it, CameraCharacteristics.LENS_FACING) }

        }catch (e: CameraAccessException){
            Log.d("tag", "Camera Exception ${e.reason}")
        }
        return deviceId[0]
    }

    @SuppressLint("MissingPermission")
    private fun connectCamera(){
        val deviceId = cameraId(CameraCharacteristics.LENS_FACING_BACK)
        Log.d("tag", "deviceid = $deviceId")
        try {
            cameraManager.openCamera(deviceId, deviceStateCall, backgroudHandler)
        }catch (e: CameraAccessException){
            Log.d("tag", "camera acces excetion ")
        } catch (e: InterruptedException){
            Log.d("tag", "camera InterruptedException excetion ")
        }

    }


    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val surfaceListener = object: TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
            Log.d("tag", "texturesurface with = $p1 and $p2" )
            openCamera()
        }
        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
        }
        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture) = true
        override fun onSurfaceTextureUpdated(p0: SurfaceTexture) = Unit
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION)
    private fun checkCameraPermission(){
        if( EasyPermissions.hasPermissions(requireActivity(), Manifest.permission.CAMERA)){
            Log.d("tag", "has camera permissions")
            connectCamera()
        }else{
            EasyPermissions.requestPermissions(requireActivity(), "Camera request needed",
                REQUEST_CAMERA_PERMISSION, Manifest.permission.CAMERA)
        }
    }


    private fun openCamera(){
        checkCameraPermission()
    }

    override fun onPause() {
        stopBackgroundThread()
        closeCamera()
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if( binding.cameraView.isAvailable){
            openCamera()
        }else
        {
            binding.cameraView.surfaceTextureListener = surfaceListener
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)






        //binding.cameraView

        binding.buttonFirst.setOnClickListener {
            stopMediaRecorder()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}