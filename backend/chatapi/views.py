from django.http import JsonResponse
import json
from openai import OpenAI
from django.views.decorators.csrf import csrf_exempt
import os
from dotenv import load_dotenv

# 🔐 .env 파일 로드
load_dotenv()

# 🔐 환경변수에서 API Key 가져오기
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

client = OpenAI(api_key=OPENAI_API_KEY)

@csrf_exempt
def chat_api(request):
    if request.method != "POST":
        return JsonResponse({"error": "POST only"}, status=400)

    try:
        body = json.loads(request.body)
        user_msg = body.get("message", "")

        messages_for_gpt = [
            {
                "role": "system",
                "content": (
                    "너는 '모아봄 챗봇'이야. 너무 반복적인 인사나 "
                    "정형화된 문장은 하지 말고, 사용자의 질문에 맞춰 "
                    "친절하고 자연스럽게 응답해. 도움을 요청하면 지원금 "
                    "정보나 안내를 제공하는 역할이야."
                )
            },
            {"role": "user", "content": user_msg}
        ]

        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages_for_gpt
        )

        reply = response.choices[0].message.content

        return JsonResponse({"reply": reply})

    except Exception as e:
        return JsonResponse({"error": str(e)}, status=500)
