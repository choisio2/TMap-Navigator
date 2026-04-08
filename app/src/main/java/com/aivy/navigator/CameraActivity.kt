package com.aivy.navigator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aivy.navigator.databinding.ActivityCameraBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    // CameraX 관련 변수
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var generativeModel: GenerativeModel
    private var selectedBitmap: Bitmap? = null

    companion object {
        private const val TAG = "CameraX_Gemini"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Camera 권한 확인 및 요청
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        //  Gemini 모델 초기화
        generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash-lite",
            apiKey = BuildConfig.GEMINI_API_KEY
        )

        // 촬영 버튼 클릭 리스너
        binding.btnCaptureAndSend.setOnClickListener {
            // 갤러리 모드였다면 카메라로 복귀
            binding.viewFinder.visibility = View.VISIBLE
            binding.ivSelectedImage.visibility = View.GONE
            binding.btnAnalyzeGallery.visibility = View.GONE
            selectedBitmap = null

            takePhotoAndAnalyze()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 갤러리 버튼
        binding.btnPickGallery.setOnClickListener { openGallery() }

        // 갤러리 이미지 AI 분석 버튼
        binding.btnAnalyzeGallery.setOnClickListener {
            selectedBitmap?.let { sendImageToGemini(it) }
        }
    }

    /** 📸 CameraX: 카메라 미리보기 시작 */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 프리뷰 설정
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // 이미지 캡처 설정
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // 후면 카메라 선택
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /** 📸📸📸 사진 촬영 및 AI 분석 메인 로직 */
    private fun takePhotoAndAnalyze() {
        val imageCapture = imageCapture ?: return

        binding.progressBarAI.visibility = View.VISIBLE
        binding.btnCaptureAndSend.isEnabled = false
        binding.tvAiResponse.text = "사진 촬영 중..."

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()

                    if (bitmap != null) {
                        sendImageToGemini(bitmap)
                    } else {
                        resetUIWithError("이미지 변환 실패")
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    resetUIWithError("촬영 실패: ${exc.message}")
                }
            }
        )
    }

    // ========================
    // 갤러리 관련
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                // 640x480으로 리사이즈 (Gemini 전송용)
                val resized = Bitmap.createScaledBitmap(original, 640, 480, true)
                selectedBitmap = resized

                // UI: 카메라 프리뷰 숨기고 선택한 이미지 표시
                binding.viewFinder.visibility = View.GONE
                binding.ivSelectedImage.visibility = View.VISIBLE
                binding.ivSelectedImage.setImageBitmap(original)

                // "AI 분석" 버튼 표시
                binding.btnAnalyzeGallery.visibility = View.VISIBLE
                binding.tvAiResponse.text = "사진이 선택되었습니다. 'AI 분석' 버튼을 눌러주세요."
            } catch (e: Exception) {
                Toast.makeText(this, "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    /** ⭐⭐⭐ Gemini API 연동 및 결과 처리 */
    private fun sendImageToGemini(bitmap: Bitmap) {
        binding.tvAiResponse.text = "Gemini가 분석 중..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val promptText = "이 사진은 사용자 전방입니다. 경로 지시: 우회전. 보이는 랜드마크를 활용해 짧고 명확한 안내 멘트를 만들어주세요."

                val inputContent = content {
                    image(bitmap)
                    text(promptText) // 👈 이제 여기서 에러가 나지 않습니다!
                }

                val response = generativeModel.generateContent(inputContent)

                withContext(Dispatchers.Main) {
                    binding.tvAiResponse.text = response.text ?: "응답 결과가 없습니다."
                    binding.progressBarAI.visibility = View.GONE
                    binding.btnCaptureAndSend.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "========== Gemini API 에러 발생 ==========")
                Log.e(TAG, "에러 종류: ${e.javaClass.simpleName}")
                Log.e(TAG, "에러 메시지: ${e.message}")
                Log.e(TAG, "상세 스택트레이스:", e)
                Log.e(TAG, "==========================================")

                withContext(Dispatchers.Main) {
                    resetUIWithError("Gemini 오류 확인 요망 (로그캣 참조)")
                }
            }
        }
    }

    /** 🛠️ 유틸: ImageProxy를 Bitmap으로 변환 및 리사이징 */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val targetWidth = 640
        val targetHeight = 480

        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())

        val scaleWidth = targetWidth.toFloat() / originalBitmap.width
        val scaleHeight = targetHeight.toFloat() / originalBitmap.height
        matrix.postScale(scaleWidth, scaleHeight)

        return Bitmap.createBitmap(
            originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
        )
    }

    private fun resetUIWithError(message: String) {
        binding.tvAiResponse.text = message
        binding.progressBarAI.visibility = View.GONE
        binding.btnCaptureAndSend.isEnabled = true
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }


    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}