# Spring Boot·Oracle 연동

## 실행 환경

Java 8 유지 요구에 맞춰 Spring Boot 2.7.18, Maven 3.9.9, Spring JDBC/JdbcTemplate, ojdbc6 11.2.0.4를 사용합니다. Java 8 개발 환경에 맞춘 구성이고 Spring Boot 2.7은 일반 커뮤니티 지원이 종료된 계열이므로 외부 운영 배포 전에는 JDK·프레임워크 업그레이드를 검토해야 합니다. 현재 서버는 `127.0.0.1`에만 바인딩합니다.

```powershell
.\run-server.cmd
# preview.cmd도 동일한 백엔드를 실행합니다.
```

Maven과 다운로드 캐시는 `.work/`에 보관합니다. 빌드에는 `javac`가 있는 JDK를 사용하도록 실행 도구에서 JAVA_HOME을 지정합니다. 서버가 실행 중이면 먼저 종료한 뒤 빌드해야 Windows JAR 파일 잠금 오류를 피할 수 있습니다.

DB 계정·비밀번호는 기존 `src/main/resources/application-local.properties`를 유지합니다. 이 파일은 Git에서 제외되고, **Maven 리소스 복사·JAR 패키징에서도 제외**됩니다. 애플리케이션 시작 시 작업 폴더의 파일을 외부 설정으로 읽습니다. JAR는 저장소 루트에서 실행하세요. Spring Boot에서는 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 환경변수로도 설정할 수 있습니다. 독립 `check-oracle.cmd` 도구의 `CAMPUSRENT_DB_*` 환경변수와 구분합니다.

## 저장 구조

- 계정: APP_USER. 회원가입은 STUDENT로 고정하며 PBKDF2 해시를 저장합니다. 기존 초기 계정 해시를 그대로 검증할 수 있습니다.
- 자원: EQUIPMENT·MODEL·TYPE·SPEC·COMPONENT·ALTERNATIVE, FACILITY·PURPOSE·HOURS·BLOCK에서 조회합니다. API 식별자는 `E1`, `F1`처럼 자원 유형과 DB ID를 조합합니다.
- 신청: RENT_REQUEST 묶음, REQUEST_ITEM 항목, REQUEST_STATUS_LOG 이력을 한 트랜잭션으로 저장합니다.
- 출고: EQUIPMENT_CHECKOUT과 CHECKOUT_COMPONENT에 당시 관리번호·구성품·전달 수량을 보존합니다.
- 반납: RETURN_INSPECTION과 RETURN_COMPONENT에 회차별 반환 수량을 저장하고 누적 수량과 출고 수량을 대조합니다.
- 점검: EQUIPMENT 상태·버전과 EQUIPMENT_CHECK_LOG를 함께 변경합니다.
- 시설 이용: FACILITY_USAGE의 시작·종료 확인과 REQUEST_ITEM 상태 변경을 함께 저장합니다.

브라우저 IndexedDB는 저장에 사용하지 않습니다. 예전 브라우저 시연 기록은 자동 이전하지 않습니다. 제출 전 선택 목록만 sessionStorage에 보관합니다. 자원 목록은 서버에서 조회하지만 활동 문구 해석과 추천 조합은 기존 브라우저 규칙을 사용하며, 실제 제출·승인 시에는 서버가 DB의 일정·정원·허용 목적·운영 시간·상태를 재검증합니다. 요일별 운영 시간은 API에서 읽어 일반 조회·대체 일정 검색에도 반영하며 날짜별 최종 검증은 서버가 수행합니다.

## API

2026-11-18 관리자 장비·대체 관계·시설 운영 화면을 추가했습니다. 상세 범위·안정성 시험·DB 영향은 [작업 기록](worklogs/2026-11-18-session.md)에 정리했습니다.

| 경로 | 동작 |
| --- | --- |
| GET /api/session | 서버 세션의 사용자 프로필·CSRF 토큰 |
| POST /api/login, /api/signup, /api/logout | 로그인·학생 가입·로그아웃 |
| GET /api/catalog | Oracle 장비·시설 목록과 사양·구성품 |
| GET /api/state | 본인 또는 관리자 범위 신청·검수 이력, 공개 예약 시간·점검 상태 |
| POST /api/requests | 선택 항목을 Oracle 신청으로 저장 |
| POST /api/requests/{id}/transition | 승인·거절·취소·출고·반납·시설 이용 상태 변경 |
| POST /api/equipment/{id}/inspection-complete | 관리자 점검 완료 |
| GET /api/admin/manage | 관리자 관리 대상·모델·구성품·운영 조건 조회 |
| POST /api/admin/equipment, /api/admin/equipment/{id} | 장비·기본 구성품 등록/수정 |
| POST /api/admin/alternatives | 방향별 대체 관계 등록/수정/비활성화 |
| POST /api/admin/facilities, /api/admin/facilities/{id} | 시설·목적·요일별 시간 등록/수정 |
| POST /api/admin/facilities/{id}/blocks, /api/admin/facilities/{id}/blocks/{blockId} | 시설 이용 제한 등록/수정 |

변경 API는 세션에 저장된 CSRF 토큰을 `X-CSRF-Token`으로 요구합니다. HttpOnly·SameSite=Lax 쿠키를 사용하며 로그인 시 세션 ID를 갱신합니다. 사용자 역할·소유권은 서버 세션을 기준으로 확인합니다. 클라이언트에서 전달한 사용자·권한은 신뢰하지 않습니다. 신청자 정보는 본인·관리자에게만 제공하고 공개 예약은 자원·시간·상태만 제공합니다.

처리 순서는 **자원 행 잠금 → 신청 행 잠금 → 버전·상태·조건 검사 → 업무 기록·상태 이력 저장**입니다. 묶음 신청은 자원 ID를 정렬해 잠급니다. 오래된 버전, 중복 승인, 운영 제한, 기존 미반납 장비의 재출고, 초과 반환과 미반납 완료를 차단합니다. DB 오류가 발생하면 해당 업무 트랜잭션을 롤백합니다. 승인 프로시저를 호출하지 않고 같은 검증을 Java 서비스에서 처리하므로 `08_approve.sql` 설치는 필수가 아닙니다.

## 실제 DB 작업과 검증

2026-11-16 기존 테이블·시퀀스를 유지한 채 `04_codes.sql`, `05_users.sql`, `06_demo_data.sql`의 초기 데이터를 빈 대상 테이블에 한 트랜잭션으로 입력했습니다. 초기 계정 3개·장비 7개·시설 5개·구성품 24개를 확인했습니다. 전체 설치 파일이나 스키마 재생성은 실행하지 않았습니다.

실제 Oracle 통합 시험 7개:

```powershell
# tools/setup-maven.ps1로 Maven 준비 후 저장소 루트에서 실행
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent (Get-Command javac).Source)
.work/apache-maven-3.9.9/bin/mvn.cmd '-Dmaven.repo.local=.work/m2' test '-Doracle.integration=true'
```

가입·해시·로그인, 소유권·관리자 권한·중복 승인·버전 충돌, 출고·보류·추가 반환·점검, 초과 반환, 시설 시작·종료, 정원 제한, 공개 응답의 개인정보 제외를 검증했습니다. 시험 행은 롤백하지만 Oracle 시퀀스 번호에는 공백이 생길 수 있습니다. 기본 `mvn test`에서는 이 외부 DB 시험을 실행하지 않습니다.

서버 실행 후 Chrome 검증:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/check-ui.ps1
```

실제 화면 로그인·신청·새 문서 재조회·승인·출고 스냅샷·반납, CSRF·공개 응답·이미지 로드·검색 결과 없음 등 11개 확인이 통과했습니다. 이 시험은 실제 업무 API를 통해 신청을 생성하고 반납 완료까지 처리하므로 DB에 검증 신청 이력이 남습니다. `.work/browser-check-result.txt`에 결과, `.work/home-desktop.png`·`home-mobile.png`에 화면 캡처를 저장합니다. 이전 `tools/browser-check.html`은 정적 시연 당시의 참고 파일이며 현재 연동 검증에는 사용하지 않습니다.

학교 계정 연동·운영 정책 확정·관리 기준 편집·외부 배포는 별도 범위입니다. Spring Boot의 자동 SQL 초기화는 꺼져 있어 서버를 재시작해도 설치 SQL이나 초기 데이터가 다시 실행되지 않습니다.

참고: [Spring Boot 2.7.18의 Java·Maven 요구사항](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html), [Spring Boot 2.7.18 릴리스·지원 안내](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/).

관리자 Oracle 통합 시험 7개를 추가해 기본 업무 7개와 함께 14개를 검증했습니다. 관리자 브라우저 확인은 tools/check-ui.ps1 -Management로 실행하며 11개가 통과했습니다. 기본 구성품·요일·허용 목적 정의는 수정 시 교체하지만 과거 출고 스냅샷과 신청 이력은 보존합니다. 장비·시설은 이용 중지로, 대체 관계는 비활성화로 관리하며 시설 제한은 등록·수정만 지원합니다.
# 후속 관리자 기능 및 신청 출처

- `/admin/models`: 품목·모델·사양 항목과 사양값 편집. `GET /api/admin/models`, `POST /api/admin/types[/{id}]`, `/api/admin/spec-items[/{id}]`, `/api/admin/models[/{id}]`를 사용합니다. 수정 시 서버가 반환한 revision을 검사합니다. 승인·미반납 모델 변경과 품목 사용 중지를 차단합니다. 비활성 품목의 자산은 과거 내역 조회를 위해 카탈로그에 유지하되 신청은 차단합니다.
- 시설 제한 해제: `POST /api/admin/facilities/{id}/blocks/{blockId}/cancel`에 시설 version과 필수 reason을 전달합니다. 기존 제한은 삭제하지 않으며 등록·수정·해제의 전후 값·처리자·시각을 동일 트랜잭션으로 기록합니다. `GET /api/admin/facilities/{id}/block-history`는 관리자 전용입니다.
- 새 앱 실행 전 `10_block_audit.sql` 증분이 필요합니다. 현재 팀 DB에는 적용했으며 다른 환경은 11번 SQL로 확인합니다. 앱 시작 시 DDL은 실행하지 않습니다.
- 신청 제출의 `originCode`는 DIRECT/PURPOSE/ALTERNATE이며 `activity`와 대체 선택의 `original.resourceId`, `original.condition`을 저장합니다. 다른 일정은 동일 자원·이용 길이, 대체 장비는 지정 방향·같은 일정을 검사합니다. 원래 조건·선택 경로는 본인·관리자 내역에만 노출합니다. 대체안 선택 후 일정 조건을 직접 바꾸면 직접 조회 경로로 처리합니다.
- 관리 편집은 기본 정의를 교체할 수 있지만 이미 출고한 구성품 스냅샷·상태 이력은 삭제하지 않습니다. 사양 정의나 자산 삭제 API는 제공하지 않습니다.

남은 상세 검수 요구는 [구현 대응표](requirements-status.md), 배포 준비는 [배포 절차](deployment.md)를 참고하세요.

## 구성품 손상·검수 회차·사후 출고

기존 ERD 컬럼을 사용하므로 추가 DDL이나 설치 SQL 실행은 필요 없습니다.

- 반납 전이의 `details.components`는 구성품 이름을 키로 하며 `{condition, note}`를 전달합니다. condition은 NORMAL/DAMAGED/MISSING이고 빈 값은 이번 수량·이전 누적량에 따라 판정합니다. 손상은 이번 반환 수량이 있어야 하며 메모가 필수입니다. 미반납 상태에 양수 반환량을 입력할 수 없고 누적 초과·부족 상태의 완료는 차단합니다. 기존 quantities 전용 호출도 호환됩니다.
- 손상이 발견되거나 점검 요청이 있으면 같은 트랜잭션으로 EQUIPMENT를 CHECKING으로 전환합니다. 이전 회차의 손상도 후속 완료 시 누락하지 않습니다. 장비는 관리자의 별도 점검 완료 전까지 신규 승인에서 제한됩니다. 정상 반납 완료는 손상 기록을 삭제하지 않습니다.
- `GET /api/requests/{id}/inspection-history`는 관리자와 신청 학생 본인에게만 출고 스냅샷·실제/등록 시각·등록자·출고 사유, 회차별 반환량·누적량·상태·메모·처리자·시각을 제공합니다. 서버에서 소유권을 검사합니다.
- 승인된 장비 신청의 출고 전이에 `details.source=BACKFILL`, `actualAt`, 필수 `reason`, `confirmed=true`, 당시 `parts` 배열(name/quantity/condition/note)을 전달하면 사후 출고로 저장합니다. 미래 시각·중복 등록·기존 실제 대여 구간과 중복되는 시각·학생 호출을 차단합니다. 현재 구성품 정의와 이름·순서를 확인하고 실제 전달 수량·상태를 입력합니다. 손상·미전달 출고 구성품은 메모가 필요하며 본체 전달 수량은 양수여야 합니다. 당시 전달 수량이 후속 반납의 기준이 됩니다.
- 일반 출고는 서버 현재 시각과 SOURCE_CODE=ACTUAL로 저장합니다. 사후 등록도 현재 등록 시각과 실제 출고 시각을 분리하며 승인·출고 이력 시각을 과거로 바꾸지 않습니다. 이미 출고한 기록을 덮어쓰는 수정 API는 제공하지 않습니다.

추가 Oracle 시험은 `InspectionOracleTest`, 실제 화면 시험은 다음 명령으로 실행합니다. Oracle 시험은 롤백하고 브라우저 시험은 전용 자산을 생성한 뒤 완료·점검 완료·이용 중지하여 이력을 남깁니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/check-ui.ps1 -Inspection
```

## 인증·장애·페이지 조회 보완

추천 추가: `GET /api/recommendation-rules`는 활성 목적·키워드·장비 규칙을 공개 조회합니다. `GET /api/admin/recommendations`, `POST /api/admin/recommendations/keywords[/{id}]`는 관리자 전용입니다. 수정 시 기존 값 비교로 충돌을 차단하고 사용 해제는 ENABLED_YN=N으로 보존합니다. 12번 SQL은 데이터만 추가하며 현재 팀 DB에는 적용했습니다. [추천 안내](recommendations.md)에 문맥 구분·시연 시설 연결·잔여 범위를 설명합니다.

운영 상세·제한은 [operations.md](operations.md)를 기준으로 확인합니다.

| API | 동작 |
| --- | --- |
| GET /api/client-config | 로컬 시연 계정 안내 표시 여부. 운영 차단 여부에 따라 숨김 |
| POST /api/account/password | currentPassword·newPassword 검증 후 해시 변경, 현재 서버의 같은 사용자 세션 종료 |
| GET /api/state?page=0&size=25&view=current | 최신 페이지와 pageInfo. view는 all/current/history, size 최대 100 |
| POST /api/requests | requestKey UUID가 있으면 동일 사용자·내용의 재시도에 기존 항목 ID 반환. 키 재사용 시 내용이 다르면 거부 |
| GET /api/health/live | DB와 독립한 앱 생존 상태 |
| GET /api/health/ready | Oracle SELECT 1 성공 시 200, 장애 시 상세 정보 없는 503 |

시도 제한은 HTTP 429·Retry-After를 반환합니다. 기존 세션·CSRF 검사는 유지하며 새 가입·변경 비밀번호는 12자 이상입니다. 실제 운영 관리자 계정은 아직 지정하지 않았습니다. REQUEST_NO는 기존 CR-숫자와 멱등 요청의 I-UUID 압축 형태가 함께 존재하며 기존 번호나 객체 이름은 바꾸지 않습니다. 기존 SQL 설치·DDL은 실행하지 않았습니다.

재실행: Oracle 시험 `-Doracle.integration=true`, 독립 트랜잭션 시험 `-Doracle.stability=true -Dtest=OracleStabilityTest`, 브라우저 시험 `tools/check-ui.ps1 -Operations`, 조회 측정 `tools/check-performance.ps1`, 상태 측정 `tools/check-health.ps1`입니다. 대량 부하 시험·실제 네트워크 장애 시험·운영 백업/복구·외부 배포를 완료한 것으로 표시하지 않습니다.
