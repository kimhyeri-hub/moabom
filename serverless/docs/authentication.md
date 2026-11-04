# 모아봄 인증

React와 Java가 같은 HS256 JWT를 사용합니다. Django 계정 코드는 남겨 두었지만, 화면은 Java `127.0.0.1:8080`만 호출합니다. Java는 클라이언트가 보낸 사용자 ID를 신뢰하지 않고 토큰의 `sub`와 비교합니다.

## 토큰

- 서명 알고리즘은 HS256만 허용합니다. `none`과 다른 `alg`는 거절합니다.
- `iss`는 `moabom-auth`, `aud`는 `moabom-api`여야 합니다.
- `exp`가 현재 시각 이하이면 거절합니다. 유효 시간은 12시간입니다.
- `sub`는 계정 저장소가 발급한 숫자 사용자 ID입니다. 프로필, 행동, 추천 경로의 사용자 ID와 같습니다.
- 비밀키는 환경변수 `MOABOM_AUTH_SECRET`입니다. 값이 없으면 사용자 API는 401을 반환합니다. 비밀키는 Git에 넣지 않습니다.

## 상태 코드

- 토큰이 없거나, 서명이 틀리거나, 만료되었거나, 발급자나 대상이 다르거나, 로그아웃으로 폐기된 경우 401입니다.
- 토큰은 유효하지만 경로의 사용자 ID가 `sub`와 다르면 403입니다.
- 지원금 목록과 상세는 토큰 없이 허용합니다.

## 비밀번호

새 비밀번호는 BCrypt로 저장합니다. 평문은 저장하지 않습니다.

Django `pbkdf2_sha256$반복$salt$hash` 형식은 Java가 검증할 수 있습니다. salt는 Django와 같이 문자열 자체를 UTF-8 바이트로 쓰고, hash는 표준 Base64입니다. 검증에 성공하면 그 계정의 해시를 BCrypt로 바꿉니다. Django SQLite의 기존 행은 복사하거나 삭제하지 않았습니다. 로컬 Java 저장소는 프로세스 메모리라서, 기존 Django 계정을 쓰려면 해시 문자열을 그대로 옮긴 뒤 한 번 로그인하면 됩니다.

## 로그아웃

토큰의 `jti`를 만료 시각까지 폐기 목록에 넣습니다. 로컬 서버는 메모리 목록을 사용합니다. AWS용 준비 저장소는 DynamoDB `jti` 파티션 키와 `expiresAt` TTL입니다. 이 템플릿은 배포하지 않았습니다. 폐기 목록이 없는 Lambda는 로그아웃을 성공으로 위장하지 않고 500을 반환합니다.

## 로컬 경로

- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/logout`
- `GET /auth/me`
- `PATCH /auth/me`
- `GET /auth/login-id?loginId=`
- `POST /chat`

모아챗의 시스템 프롬프트와 모델 `gpt-4o-mini`는 Django 구현과 같습니다. 로컬 서버는 OpenAI를 호출하지 않고 고정 확인 문장을 반환합니다. Lambda용 클라이언트만 `OPENAI_API_KEY`가 있을 때 호출하며, 테스트는 그 호출을 모킹합니다.
