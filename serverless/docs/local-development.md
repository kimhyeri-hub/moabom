# 모아봄 로컬 추천 실행

이 문서는 AWS에 배포하지 않고 기존 React 화면에서 Java 인증, 추천, 모아챗을 실행하는 방법을 정리한 것입니다. 로컬 HTTP 서버는 AWS Lambda가 아니며 AWS를 호출하지 않습니다. 사용자 API는 Java가 발급한 토큰이 필요하며, 로컬 서버는 `127.0.0.1`에만 바인딩합니다. 인증 방식은 `authentication.md`에 있습니다.

## 시스템 구성

브라우저의 React 개발 서버가 로컬 Java HTTP 서버를 호출합니다. Java 서버는 메모리 저장소에 프로필과 행동을 두고, AWS에 올릴 때 쓰는 Lambda 핸들러와 `RecommendationScorer`를 그대로 호출합니다.

```text
React (CRA, 포트 3000)
  REACT_APP_API_BASE_URL=http://127.0.0.1:8080
        |
        | 127.0.0.1 only, CORS: localhost:3000 / 127.0.0.1:3000
        v
LocalApiServer (com.sun.net.httpserver)
        |
        +-- ListGrantsHandler, GetGrantHandler
        +-- SignupHandler, LoginHandler, LogoutHandler, MeHandler, LoginIdHandler
        +-- ChatHandler
        +-- PutProfileHandler, GetProfileHandler
        +-- PostBehaviorHandler, DeleteScrapHandler, ListBehaviorsHandler
        +-- RecommendHandler
                |
                +-- RecommendationScorer
                +-- InMemoryGrantsRepository      local-grants.json
                +-- InMemoryAccountRepository
                +-- InMemoryUserProfileRepository
                +-- InMemoryBehaviorRepository
```

AWS에 올릴 때의 구성은 이와 다릅니다. SAM 템플릿은 API Gateway, Java 25 Lambda, DynamoDB 세 테이블을 정의합니다.

- 지원금 테이블: 파티션 키 `entityType`, 정렬 키 `grantId`. 목록은 Query, 상세는 GetItem입니다.
- 사용자 테이블: 파티션 키 `userId`. 속성 `age`, `region`, `incomeBracket`.
- 행동 테이블: 파티션 키 `userId`, 정렬 키 `behaviorSk`. 새 기록의 정렬 키는 `VIEW#지원금ID` 또는 `SCRAP#지원금ID`입니다.

사용자 API용 Lambda는 템플릿 매개변수 `EnableUnauthenticatedUserRoutes`가 `true`일 때만 생성됩니다. 기본값은 `false`입니다. 경로가 만들어져도 핸들러는 유효한 토큰이 없으면 401을 반환합니다. 이번 작업에서는 스택을 배포하지 않았고 DynamoDB에도 접속하지 않았습니다.

Django 챗봇 코드는 `backend/chatapi`에 그대로 남아 있습니다. 화면의 모아챗은 Java `POST /chat`을 호출합니다. 로컬 서버는 OpenAI를 호출하지 않습니다.

## 추천 계산

계산식과 자격 규칙은 `recommendation-algorithm.md`와 같습니다. 가중치는 실험으로 맞춘 값이 아닙니다.

```text
score = 0.6 × contentSimilarity + 0.4 × behaviorInterest
behaviorInterest = 0.5 × directAction + 0.5 × categoryAffinity
```

조회 가중치는 1, 스크랩 가중치는 2입니다. 명확히 자격 미달이면 제외하고, 조건이 비어 있으면 `CHECK_REQUIRED`로 남깁니다. 최대 5건입니다. 점수가 같으면 `ELIGIBLE`이 앞이고, 그래도 같으면 `grantId` 오름차순입니다.

스크랩 취소는 `SCRAP#지원금ID`만 지웁니다. 조회 기록은 남으므로, 상세 화면을 연 뒤 스크랩을 취소하면 조회 가중치가 다음 추천에 남습니다. 조회가 없는 상태에서 스크랩을 취소하면 그 지원금의 행동 가산이 사라집니다.

## API

공통 사용자 응답에는 `localDevOnly: true`와 헤더 `X-Moabom-Local-Dev: authenticated-user-route`가 있습니다. 식별자는 영문, 숫자, 하이픈이며 첫 글자는 영문 또는 숫자입니다. 최대 80자입니다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/grants` | 예시 지원금 목록 |
| GET | `/grants/{id}` | 지원금 상세. 없으면 404 |
| PUT | `/users/{id}/profile` | 프로필 저장. 처음이면 201, 수정이면 200 |
| GET | `/users/{id}/profile` | 프로필 조회. 없으면 404 |
| POST | `/users/{id}/events` | `VIEW` 또는 `SCRAP` 기록. 생성 201, 중복 200 |
| DELETE | `/users/{id}/events` | 스크랩만 삭제. 본문 `type`은 `SCRAP` |
| GET | `/users/{id}/behaviors` | 남은 행동 이력 |
| GET | `/users/{id}/recommendations` | 개인화 추천 최대 5건 |

프로필 본문 예:

```json
{"age": 25, "region": "예시특별시", "incomeBracket": 3}
```

연령은 0 이상 120 이하 정수, 지역은 1자 이상 100자 이하, 소득분위는 1 이상 10 이하 정수입니다.

행동 본문 예:

```json
{"type": "SCRAP", "grantId": "example-006-care"}
```

스크랩 취소가 성공하면 `deleted`가 true입니다. 같은 취소를 다시 보내면 200이고 `deleted`는 false입니다. `type`이 `VIEW`이면 400이며 조회 기록은 지우지 않습니다.

추천 항목은 `rank`, `score`, `contentSimilarity`, `behaviorInterest`, `eligibilityStatus`, `reasons`, `grant`를 포함합니다. `eligibilityStatus`는 `ELIGIBLE` 또는 `CHECK_REQUIRED`입니다.

## 예시 데이터

`src/main/resources/local-grants.json`의 8건은 모두 `sample: true`이고 제목이 `[예시]`로 시작합니다. 설명에 실제 정책이 아니라고 적혀 있습니다. 링크는 `example.com`입니다.

로컬 확인용 프로필은 연령 25, 지역 예시특별시, 소득 3분위입니다. 이 조건에서 `example-001`, `004`, `005`, `006`은 자격 충족이고 `002`, `003`, `007`, `008`은 제외됩니다. 행동이 없으면 첫 추천은 `example-001-youth-transport`입니다. `example-006-care`를 스크랩하면 그 지원금이 1위가 됩니다.

React는 로그인한 계정의 숫자 사용자 ID를 사용합니다. 고정 `local-user`는 사용하지 않습니다. API 주소는 `frontend/.env`의 `REACT_APP_API_BASE_URL`입니다. 기본값은 `http://127.0.0.1:8080`입니다. 회원가입과 로그인도 이 주소의 `/auth` 경로를 사용합니다. 회원가입 화면의 연령대는 20대 25세, 30대 35세, 40대 45세, 50대 55세, 60대 이상 65세로 바꿉니다. 가구 형태와 특수조건도 프로필에 저장되어 자격 검증에 사용됩니다. 공개 정책 수집은 아직 하지 않습니다.

## 로컬 실행

테스트:

```text
cd serverless/grants-api
mvn -B test
```

서버:

```text
cd serverless/grants-api
mvn -q -DskipTests package
java -cp target/grants-api-0.1.0.jar com.moabom.grants.local.LocalApiServer
```

포트는 첫 인자 또는 환경변수 `LOCAL_API_PORT`입니다. 기본 포트는 8080이고, 바인딩 주소는 항상 `127.0.0.1`입니다.

프론트:

```text
cd frontend
npm install
npm start
```

개발 서버 주소는 `http://localhost:3000`이어야 CORS를 통과합니다.

## 이번 실행에서 확인한 결과

2026-11-04 12:18:48 KST, `mvn -B test`는 BUILD SUCCESS입니다. 테스트 28개, 실패 0, 오류 0, 건너뜀 0, 소요 7.904초입니다.

- DeleteScrapFlowTest 2
- GetGrantHandlerTest 3
- GrantMapperTest 1
- ListBehaviorsHandlerTest 1
- ListGrantsHandlerTest 4
- LocalApiServerTest 1
- RecommendationScorerTest 8
- RecommendHandlerTest 2
- SampleGrantDataTest 1
- StoredRecommendationFlowTest 5

`LocalApiServerTest`는 임의 포트의 `127.0.0.1` 서버에 HTTP로 다음 순서를 호출했습니다. 프로필 저장, 지원금 목록, 초기 1위 `example-001-youth-transport`, `example-006-care` 스크랩 후 1위 변경, 스크랩 취소 후 1위 복귀, 중복 취소, 조회 기록 유지.

프론트 `npm install` 후 첫 `npm run build`는 경고 1건과 함께 완료되었습니다. `DetailPage`의 effect 의존성 경고를 `useCallback`으로 고친 뒤 다시 빌드했고, 두 번째 빌드는 `Compiled successfully`로 끝났습니다. gzip 기준 JavaScript 80.89 kB, CSS 3.4 kB입니다.

같은 시각에 띄운 `127.0.0.1:8080` 프로세스에 대해 별도 HTTP 클라이언트로 확인한 순서는 다음과 같습니다. `netstat`에서 수신 주소는 `127.0.0.1:8080`이었습니다.

- PUT `/users/curl-user/profile` 201, 지역 예시특별시
- GET `/grants` 200, 8건, 첫 제목 `[예시] 모아봄 테스트용 청년 교통비`
- 추천 1위 `example-001-youth-transport`, 점수 0.0, ELIGIBLE
- POST 스크랩 `example-006-care` 201 이후 1위 `example-006-care`, 점수 1.0
- DELETE 스크랩 200, `deleted true` 이후 1위가 `example-001-youth-transport`, 점수 0.0으로 돌아옴
- 조회를 남긴 뒤 스크랩을 다시 취소하면 `deleted false`이고 이력은 VIEW 1건
- VIEW 삭제 요청은 400, 메시지 `스크랩만 취소할 수 있습니다. 조회 기록은 유지됩니다.`
- OPTIONS 프리플라이트 204, `Access-Control-Allow-Origin: http://localhost:3000`


AWS CLI, SAM CLI, Docker로 `sam validate` 또는 `sam local`은 실행하지 않았습니다. Lambda `java25` 지원은 배포 전에 대상 리전에서 다시 확인해야 합니다. DynamoDB 구현의 읽기, 쓰기, 삭제는 코드와 IAM 정의만 있고 실제 테이블에서 실행하지 않았습니다.

## 논문에 쓸 수 있는 설명

모아봄의 서버리스 추천은 지원금 자격 필터와 사용자 행동을 함께 보는 하이브리드 방식입니다. 자격은 연령, 지역, 소득분위로 판정하고, 관심은 조회보다 스크랩에 높은 가중치를 줍니다. 콘텐츠 유사도 0.6과 행동 관심도 0.4를 더해 상위 5건과 이유, 자격 상태를 돌려줍니다. 이 가중치는 초기 설계값입니다.

구현된 기능은 지원금 목록과 상세, Java 회원가입과 로그인, 프로필 저장, 조회와 스크랩 기록, 스크랩 취소, 저장값 기반 재추천, 로컬 모아챗입니다. 같은 핸들러를 로컬 메모리 서버와 Lambda 진입점으로 공유합니다. 로컬 서버는 루프백 주소만 열고, 배포 템플릿은 사용자 경로를 기본으로 만들지 않습니다. Django 계정과 챗봇 코드는 삭제하지 않았고 화면은 Java API를 호출합니다.

현재 결과로 쓸 수 있는 범위는 로컬 테스트와 로컬 HTTP 실행입니다. 클라우드 배포, 실서비스 인증, 운영 데이터 적재, 가중치 최적화, 가구 형태와 특수조건 반영은 이후 작업입니다. Django 기반 챗봇은 이번 추천 경로와 별도로 유지합니다.

로컬 API는 `POST /policies/import`로 JSON 또는 CSV 정책 행을 메모리 카탈로그에 추가할 수 있습니다. 이 경로는 AWS에 연결되어 있지 않고 예시 지원금 8건을 삭제하지 않습니다.
