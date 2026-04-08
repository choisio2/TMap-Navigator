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

    suspend fun analyzeImage(bitmap: Bitmap, step: RouteStep): String? {
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
            - 다음 동작: ${directionText}
            [규칙]
            - 사진에 보이는 간판, 상점명, 건물 특징을 반드시 활용할 것
            - 한국어 1문장, 25자 이내
            - "~하세요" 체로 끝낼 것
            - 간판이 없으면 "빨간 건물", "횡단보도", "큰 나무" 등 시각 특징 활용
            - 🚨 중요: 만약 사진이 너무 어둡거나, 흔들렸거나, 특징적인 랜드마크를 도저히 파악할 수 없다면 오직 숫자 "0"만 출력할 것.
            - 안내 멘트(또는 "0")만 출력하고 다른 설명은 절대 금지.
        """.trimIndent()

        val inputContent = content {
            image(bitmap)
            text(promptText)
        }

        val response = generativeModel.generateContent(inputContent)
        return response.text
    }

    suspend fun enhanceNavigationText(originalInstruction: String, landmarks: String): String {
        val promptText = """
            당신은 길눈이 어두운 사람을 위한 내비게이션 안내 AI입니다.
            다음 TMap의 기본 경로 안내와 회전 지점 주변에 있는 랜드마크(POI) 목록을 참고하여,
            사용자가 헷갈리지 않고 훨씬 알아듣기 쉬운 자연스러운 1문장의 안내 멘트를 만들어주세요.

            [입력 데이터]
            - 원래 안내: $originalInstruction
            - 주변 랜드마크: $landmarks

            [규칙]
            - 랜드마크 중 가장 눈에 띄거나 방향을 잡기 좋은 1~2개만 선택할 것.
            - "~하세요" 체로 끝낼 것.
            - 예시: "오른쪽에 있는 스타벅스를 끼고 우회전하세요." 또는 "정면에 보이는 CU 편의점 앞에서 왼쪽 골목으로 들어가세요."
            - 부가 설명 없이 오직 안내 멘트 1문장만 출력할 것.
            - 🚨 만약 랜드마크 정보가 부족하거나 어색하면 기본 안내만 부드럽게 다듬어서 출력할 것.
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(promptText)
            response.text?.trim() ?: originalInstruction
        } catch (e: Exception) {
            originalInstruction // 에러 시 원래 안내로 폴백
        }
    }
}