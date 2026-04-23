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

    // 파라미터에 azimuth(방위각) 추가
    suspend fun analyzeImage(bitmap: Bitmap, step: RouteStep, azimuth: Float): String? {
        val directionText = when (step.turnType) {
            12 -> "왼쪽으로 회전"
            13 -> "오른쪽으로 회전"
            14 -> "뒤로 돌아가기(유턴)"
            211, 212, 213, 214, 215, 216, 217 -> "횡단보도 건너기"
            else -> step.description
        }

        val promptText = """
            보행자 음성 내비게이션 안내 멘트를 생성하세요.
            [상황]
            - 이 사진은 보행자의 전방 시점입니다.
            - 현재 사용자의 몸이 향한 방향: ${azimuth}도 (0:북, 90:동, 180:남, 270:서)
            - 다음 동작: ${directionText}
            [규칙]
            - 사진에 보이는 간판, 상점명, 건물 특징을 반드시 활용할 것
            - 한국어 1문장, 25자 이내, "~하세요" 체로 끝낼 것
            - 사진 특징 파악 불가 시 오직 "0"만 출력할 것.
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

    // 파라미터에 azimuth(방위각) 추가
    suspend fun enhanceNavigationText(originalInstruction: String, landmarks: String, azimuth: Float): String {
        val promptText = """
            내비게이션 안내 AI입니다. TMap의 기본 안내와 주변 랜드마크 정보를 바탕으로 자연스러운 안내 멘트를 만드세요.

            [입력 데이터]
            - 원래 안내: $originalInstruction
            - 주변 랜드마크: $landmarks
            - 현재 사용자 방향: ${azimuth}도

            [규칙]
            - 랜드마크 중 눈에 띄는 1~2개 선택하여 1문장으로 생성
            - "~하세요" 체로 끝낼 것
            - 랜드마크 정보 부족 시 원래 안내를 다듬어 출력
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(promptText)
            response.text?.trim() ?: "$originalInstruction"
        } catch (e: Exception) {
            "$originalInstruction"
        }
    }
}