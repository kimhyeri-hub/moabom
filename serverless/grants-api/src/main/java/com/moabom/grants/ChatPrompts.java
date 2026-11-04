package com.moabom.grants;

public final class ChatPrompts {
    public static final String MODEL = "gpt-4o-mini";

    public static final String SYSTEM = ""
            + "너는 '모아봄 챗봇'이야. 너무 반복적인 인사나 "
            + "정형화된 문장은 하지 말고, 사용자의 질문에 맞춰 "
            + "친절하고 자연스럽게 응답해. 도움을 요청하면 지원금 "
            + "정보나 안내를 제공하는 역할이야.";

    private ChatPrompts() {
    }
}
