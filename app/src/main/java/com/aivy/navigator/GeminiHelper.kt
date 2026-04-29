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

        // 프롬프트 강화: 랜드마크 강제 포함 지시
        val promptText = """
            [상황]
            - 이 사진은 보행자의 전방 시점입니다.
            - 현재 사용자의 몸이 향한 방향: ${azimuth}도 (0:북, 90:동, 180:남, 270:서)
            - 다음 동작: ${directionText}
            
            [엄격한 출력 규칙]
            1. 사진에 보이는 간판, 상점명, 건물 특징을 반드시 포함해서 "~하세요"로 끝나는 딱 1문장의 길 안내를 출력하세요.
            2. 인사말, 부연 설명, 마크다운(**), 따옴표(" ")는 절대 쓰지 마세요.
            3. 사진에서 특징을 찾을 수 없다면 오직 숫자 "0"만 출력하세요.
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
            - 원본 TMap 안내: $originalInstruction
            - 활용해야 할 랜드마크: $landmarks

            [엄격한 출력 규칙]
            1. 제공된 활용해야 할 랜드마크를 반드시 문장 안에 포함해서 길 안내를 만드세요.
            2. 원본 안내에 적힌 정확한 거리(m) 수치는 무시하고 과감히 빼세요. 대신 방향(좌/우/직진/건너기 등) 위주로 자연스럽게 문장을 만드세요. 
               (예시: "GS25 부근에서 우측 횡단보도를 건너세요.")
            3. "~하세요"로 끝나는 딱 1문장만 출력하세요.
            4. 인사말, 부연 설명, 마크다운(**), 따옴표(" "), 줄바꿈은 절대 포함하지 마세요. 오직 안내 멘트만 출력하세요.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(promptText)
            val result = response.text?.trim()

            // 제미나이가 빈 칸을 반환하면 안전하게 원본 리턴
            if (result.isNullOrBlank()) originalInstruction else result
        } catch (e: Exception) {
            originalInstruction
        }
    }
}