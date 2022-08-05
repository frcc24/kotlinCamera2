package br.com.cameraapi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import br.com.cameraapi.databinding.FragmentFirstBinding
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.lang.IllegalArgumentException
import java.util.*


const val REQUEST_CAMERA_PERMISSION = 100

class FirstFragment : Fragment() {

    private val MAX_PREVIEW_WIDTH = 1000;
    private val MAX_PREVIEW_HEIGHT = 1000;

    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder

    private lateinit var cameraDevice: CameraDevice
    private val deviceStateCall = object: CameraDevice.StateCallback(){
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

    private fun previewSession(){
        val surfaceTexture = binding.cameraView.surfaceTexture
        if (surfaceTexture != null) {
            surfaceTexture.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
            val surface = Surface(surfaceTexture)

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                override fun onConfigured(session: CameraCaptureSession) {
                    if( session != null){
                        captureSession = session
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)

                    }
                }

                override fun onConfigureFailed(p0: CameraCaptureSession) {
                    Log.d("tag", "creatig capture session failed")
                }

            }, null)
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
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}