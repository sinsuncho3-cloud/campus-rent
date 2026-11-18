# CampusRent · Oracle 11g XE 구축 SQL

ERD와 현재 화면을 매핑해 작성한 **개발 환경용 SQL**입니다. 2026-11-16 사용자 요청에 따라 `01_tables.sql`·`02_sequences.sql`·`03_indexes.sql` 범위를 실행해 테이블 26개·컬럼 185개·시퀀스 17개와 관련 인덱스를 생성·검증했습니다. 이후 화면의 실제 DB 저장 구현을 위해 초기 계정 3개·장비 7개·시설 5개 등을 입력하고 Spring Boot에 연결했습니다. 승인 프로시저·전체 설치 파일은 실행하지 않았으며 업무 검증은 Java 트랜잭션에서 수행합니다. 연결 시험과 최신 상태는 [connection.md](connection.md)에 기록했습니다.

현재 스키마에는 생성된 객체가 있으므로 빈 스키마 전용 `install_dev.sql`을 재실행하지 마세요. 후속 작업은 승인된 미실행 단계만 별도로 수행해야 합니다.

Java 8 기반 연결 설정과 `SELECT 1 FROM DUAL` 단일 테스트 방법은 [connection.md](connection.md)를 참고하세요. 연결 테스트는 아래 설치 SQL을 실행하지 않습니다.

## 파일과 실행 순서

| 파일 | 내용 | 설치 시 실행 |
| --- | --- | --- |
| `install_dev.sql` | 빈 개발 스키마에 아래 파일을 순서대로 실행하는 진입점 | 직접 실행 |
| `00_preflight.sql` | SYS/SYSTEM 차단, 빈 스키마·생성 권한 확인 | 자동 |
| `01_tables.sql` | ERD의 테이블 26개·컬럼 185개, PK·FK·UNIQUE·CHECK | 자동 |
| `02_sequences.sql` | 숫자 단일 PK용 시퀀스 17개 | 자동 |
| `03_indexes.sql` | 이메일 유일성, FK·화면 조회 인덱스 | 자동 |
| `04_codes.sql` | 장비 품목·이용 목적·키워드·추천 규칙 | 자동 |
| `05_users.sql` | 해시 비밀번호를 가진 초기 관리자·학생 계정 | 자동 |
| `06_demo_data.sql` | 장비 7개·시설 5개, 모델·사양·구성품·운영 시간·대체 관계 | 자동 |
| `07_queries.sql` | 로그인·검색·추천·신청·이력·반납 합계 등 바인드 SQL 예시 | 참조용, 일괄 실행 금지 |
| `08_approve.sql` | 자원 잠금·중복 검사·버전 확인·상태 이력 포함 승인 프로시저 | 자동, 코드 데이터보다 먼저 |
| `09_verify.sql` | 구축 결과·초기 데이터·승인 기본 동작 검증 | 자동, 임시 시험 행은 롤백 |
| `mapping.md` | ERD ↔ 화면 ↔ 테이블, 초기 ID, 상태 코드·보완 규약 | 문서 |

`install_dev.sql`은 공개 시연 계정까지 포함하는 **개발용 설치**입니다. 기존 스키마에 덮어쓰지 않으며 삭제·초기화 SQL은 포함하지 않았습니다. 반복 설치를 위해 기존 객체를 자동 삭제하지 않습니다.

## 초기 로그인 데이터

| USER_ID | 이메일 | 공개 시연 비밀번호 | 권한 |
| --- | --- | --- | --- |
| 1 | admin@campusrent.example | CampusAdmin123! | ADMIN |
| 2 | demo@campusrent.example | Campus123! | STUDENT |
| 3 | other@campusrent.example | Campus123! | STUDENT |

이 계정은 DB 접속 계정이 아니라 **애플리케이션의 APP_USER 데이터**입니다. 공개된 개발용 비밀번호이므로 운영 계정으로 사용하지 않습니다. 실제 DB 접속 계정·비밀번호는 이 디렉터리에 저장하지 않습니다.

`APP_USER.PASSWORD`에는 `{pbkdf2@SpringSecurity_v5_8}` 접두사와 무작위 salt 16바이트 + PBKDF2-HMAC-SHA256(310,000회, 256비트)의 hex 값을 저장했습니다. 계정별 salt가 다릅니다. 기존 화면의 브라우저 시연 해시를 그대로 가져오지 않았습니다.

Spring Security 5.8 이상을 사용하는 서버에서는 다음처럼 검증할 수 있습니다. Spring Boot·Security 버전은 아직 프로젝트에서 확정하지 않았으므로 연동 시 해당 의존성에 맞게 적용하세요.

```java
Map<String, PasswordEncoder> encoders = new HashMap<>();
encoders.put("pbkdf2@SpringSecurity_v5_8",
    Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
PasswordEncoder encoder = new DelegatingPasswordEncoder(
    "pbkdf2@SpringSecurity_v5_8", encoders);

// DB에서 이메일로 사용자 1명을 조회한 뒤 서버에서 비교합니다.
boolean authenticated = encoder.matches(rawPassword, user.getPassword());
```

위 코드는 별도 클래스의 import·빈 설정이 필요한 연동 예시입니다. 일반 가입에서도 `encoder.encode(rawPassword)` 결과만 저장하고 사용자 입력으로 관리자 역할을 받지 마세요. `07_queries.sql`의 로그인 SELECT에는 해시가 포함되지만, 이를 브라우저 응답에 노출하면 안 됩니다.

**현재 화면은 Spring Boot API를 통해 Oracle을 조회·저장합니다.** 선택 목록만 sessionStorage에 남고 로그인은 서버 세션을 사용합니다. 실행과 실제 연동 검증은 [백엔드 안내](../../docs/backend.md)를 참고하세요.

## 외부 DB에서 실행할 때

필요 정보: 호스트, 포트, SID 또는 서비스명, 전용 스키마 계정, 접근 방법(VPN·허용 IP 등). Oracle 11g XE의 SID는 흔히 XE지만 실제 제공값을 확인해서 사용합니다. 접속 정보는 오늘 필요하지 않습니다.

전용 애플리케이션 스키마에 `CREATE SESSION`, `CREATE TABLE`, `CREATE SEQUENCE`, `CREATE PROCEDURE` 권한과 기본 테이블스페이스 할당량이 필요합니다. 초기 권한·계정 생성은 DB 관리자의 작업으로 구분합니다. 설치 스크립트는 계정 생성·권한 상승을 수행하지 않습니다.

접속 후 Oracle 11g XE 여부, 스키마 소유자, 문자셋(한글 저장 가능 여부), 테이블스페이스 할당량을 먼저 확인합니다. SQL 파일은 UTF-8이며 SQL*Plus 사용 시 클라이언트 문자셋을 UTF-8에 맞춰야 합니다. SQL Developer는 파일 인코딩을 UTF-8로 지정하고 **스크립트 실행(F5)** 을 사용합니다.

SQL*Plus 예시 (비밀번호를 명령행에 넣지 않고 프롬프트로 입력):

```text
sqlplus /nolog
CONNECT CAMPUSRENT@//db-host:1521/service-name
@C:/path/to/campus-rent/db/oracle11g/install_dev.sql
```

SID 방식의 접속 정보가 제공되면 적절한 TNS 별칭·접속 기술자를 사용합니다. 위 주소의 `service-name`을 SID라고 가정해 대입하지 않습니다. 실행 경로를 찾을 수 없다면 설치 파일 위치에서 실행하거나 전체 경로를 사용하세요. 각 `@@` 파일은 설치 파일 기준으로 읽습니다.

Oracle DDL은 암묵적으로 커밋됩니다. 따라서 실패 시 이미 생성된 테이블까지 롤백되지 않습니다. 오류가 나면 중단한 상태를 확인하고 원인을 수정해야 하며 설치 파일을 곧바로 재실행하지 않습니다. 초기 데이터 DML은 마지막 검증까지 성공한 뒤 COMMIT합니다. 실패 시 DML을 롤백하도록 설정했습니다.

시퀀스는 1000부터 시작하고 1~999는 초기 데이터에 사용합니다. 신규 행은 `SQ_테이블명.NEXTVAL`을 명시해서 INSERT해야 합니다. IDENTITY, 컬럼 기본값의 NEXTVAL, 자동 PK 트리거는 사용하지 않습니다. 롤백 시에도 시퀀스 번호에는 공백이 생길 수 있습니다.

## 확인한 것과 남은 검증

로컬 검사:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/check-db.ps1
```

JDK의 `java`·`javac`가 필요합니다. ERD 테이블·컬럼명 일치(26개·185개), 외래키 44개의 대상 키·자료형·인덱스 연결, 시퀀스 참조, 설치 파일 경로, 11g 식별자 길이를 검사했습니다. Java에서 초기 해시 3개가 해당 비밀번호에 일치하고 잘못된 비밀번호에는 불일치하며 salt가 서로 다른지 확인했습니다.

외부 DB 접속 후에는 `09_verify.sql`로 실제 DDL·프로시저와 초기 데이터 수, 관리자 승인·학생 승인 차단·중복 승인 차단·버전 충돌·시설 조건 누락을 확인합니다. 별도 두 DB 세션으로 동시 승인 경합도 시험해야 합니다. 로컬 검사 통과가 Oracle 실행 성공을 의미하지 않습니다.

승인만 실행 가능한 프로시저를 제공하며, 전체 출고·반납·시설 이용 서비스까지 구현한 것은 아닙니다. `07_queries.sql`의 다른 변경 쿼리는 행 수·권한·상태·트랜잭션 검사를 수행하는 서비스 안에서 사용해야 합니다. 자세한 보완 규칙은 `mapping.md`에 정리했습니다.

참고: [Oracle 11g CREATE SEQUENCE](https://docs.oracle.com/html/E10592_02/statements_6015.htm), [Spring Security 비밀번호 저장](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html), [Pbkdf2PasswordEncoder 5.8 API](https://docs.spring.io/spring-security/site/docs/5.8.16/api/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.html).
# 2026-11-18 증분 변경

추천 확장은 `recommendation-seed.json` → `tools/generate-recommendation-sql.ps1` → `12_recommendations.sql`로 관리합니다. 12번은 기존 테이블에 목적·키워드·장비 규칙을 추가하고 지정된 원래 시연 시설에만 새 목적을 연결하는 DML입니다. 현재 팀 DB에는 트랜잭션으로 적용했으며 DDL·삭제·기존 설치 재실행은 하지 않았습니다. 실제 시설 권한은 자동 확대하지 않습니다. 관리자 편집 이후에는 초기 사전을 다시 주입하는 것이 적절한지 먼저 검토합니다. 상세는 [추천 안내](../../docs/recommendations.md)를 참고하세요.

`01`~`09`와 `install_dev.sql`은 원본 ERD 기준입니다. 기존 운영 스키마에 재실행하지 않습니다. `10_block_audit.sql`은 시설 제한 해제 컬럼 4개와 `TBL_FACILITY_BLOCK_LOG`, `SEQ_FACILITY_BLOCK_LOG`, 관련 제약·인덱스 및 기존 제한의 IMPORT 기준 이력을 추가하는 **1회 적용용** 증분입니다. 현재 팀 DB에는 적용했습니다. 기존 테이블·SQ_ 시퀀스 이름은 변경하지 않았습니다.

새 빈 스키마는 기존 구축 절차로 기본 스키마·초기 데이터를 준비한 후 10번을 한 번 적용하고 최신 앱을 시작합니다. `09_verify.sql`은 원본 26개 테이블·185개 컬럼·17개 시퀀스 기준이므로 확장 이후의 총 개수 판정용으로 사용하지 않습니다. 증분은 읽기 전용 `11_verify_block_audit.sql`로 확인합니다. 10번과 11번은 `install_dev.sql`에서 자동 실행하지 않습니다. `08_approve.sql`은 기본 ERD용 참고 프로시저이며 현재 서비스는 Java 트랜잭션으로 승인합니다.
