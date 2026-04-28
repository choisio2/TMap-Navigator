package com.aivy.navigator

import android.graphics.Bitmap
import com.aivy.navigator.data.model.RouteStep
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

object GeminiHelper {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun analyzeImage(bitmap: Bitmap, step: RouteStep, azimuth: Float): String? {
        val directionText = when (step.turnType) {
            12 -> "왼쪽으로 회전"
            13 -> "오른쪽으로 회전"
            14 -> "뒤로 돌아가기(유턴)"
            211, 212, 213, 214, 215, 216, 217 -> "횡단보도 건너기"
            else -> step.description
        }

        val promptText = """
            [상황]
            - 이 사진은 보행자의 전방 시점입니다.
            - 현재 사용자의 몸이 향한 방향: ${azimuth}도 (0:북, 90:동, 180:남, 270:서)
            - 다음 동작: ${directionText}
            
            [엄격한 출력 규칙]
            1. 사진에 보이는 간판, 상점명, 건물 특징을 활용하여 "~하세요"로 끝나는 딱 1문장만 출력하세요.
            2. 인사말, 부연 설명, 마크다운(**), 따옴표(" ")는 절대 포함하지 마세요.
            3. 사진 특징 파악 불가 시 오직 숫자 "0"만 출력하세요.
        """.trimIndent()

        val inputContent = content {
            image(bitmap)
            text(promptText)
        }

        return try {
            val response = generativeModel.generateContent(inputContent)
            response.text
        } catch (e: Exception) { null }
    }

    suspend fun enhanceNavigationText(originalInstruction: String, landmarks: String, azimuth: Float): String {
        val promptText = """
            [입력 데이터]
            - 원래 안내: $originalInstruction
            - 주변 랜드마크: $landmarks
            - 현재 사용자 방향: ${azimuth}도

            [엄격한 출력 규칙]
            1. 랜드마크 중 눈에 띄는 1~2개를 선택하여 "~하세요"로 끝나는 딱 1문장만 출력하세요.
            2. 인사말("네, AI입니다" 등), 부연 설명, 마크다운(**), 따옴표(" "), 줄바꿈은 절대 포함하지 마세요.
            3. 오직 사용자에게 들려줄 최종 음성 멘트 텍스트 1줄만 출력해야 합니다.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(promptText)
            response.text?.trim() ?: originalInstruction
        } catch (e: Exception) {
            originalInstruction
        }
    }
}