# Oracle 연결 설정·단일 SELECT 테스트

## 현재 선택

- Java/Javac `1.8.0_151`을 유지합니다. 이번 JDBC 연결 확인에는 Java 버전 변경이 필요하지 않습니다.
- Spring Boot·Maven·Gradle은 아직 구성하지 않습니다. 기존 Java 실행 방식에 맞춘 독립 JDBC 테스트 도구를 추가했습니다. 화면 API·인증을 Oracle로 전환하는 작업은 별도입니다.
- 드라이버: Oracle 배포 `com.oracle.database.jdbc:ojdbc6:11.2.0.4`. Oracle 문서상 이 버전의 ojdbc6는 Java 8을 지원합니다. 파일명의 6이 Java 8 사용 불가를 의미하지 않습니다. 실제 서버와의 인증 호환성은 접속 시험으로 확인해야 합니다.
- 연결 방식: JDBC Thin / SID. URL은 `jdbc:oracle:thin:@203.247.53.176:80:xe`입니다. 포트 80을 그대로 사용하며 HTTP URL이나 서비스명 방식으로 변경하지 않습니다.

기존 ERD·SQL의 테이블은 `APP_USER`, `REQUEST_ITEM` 등이며 시퀀스는 `SQ_APP_USER` 등입니다. `TBL_`·`SEQ_` 규칙과 다르지만 이번 작업에서는 기존 이름을 유지하고 DB 객체를 생성·변경하지 않습니다. 규칙 적용은 별도 확인 후 진행해야 합니다.

## 로컬 계정 입력

`src/main/resources/application-local.properties`에 실제 **Oracle 스키마 로그인 계정**을 직접 입력합니다. `APP_USER`의 학생·관리자 시연 계정과는 다릅니다.

```properties
spring.datasource.url=jdbc:oracle:thin:@203.247.53.176:80:xe
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
spring.datasource.username=
spring.datasource.password=
```

두 빈 항목에 값을 입력하고 저장하세요. 파일은 UTF-8로 읽으며 Java properties 이스케이프 규칙을 따릅니다. 비밀번호에 역슬래시가 있으면 `\\`로 적고, 선행 공백이 값의 일부라면 이스케이프해야 합니다. 비밀번호를 채팅이나 명령행 인자로 보내지 마세요. 도구는 계정·비밀번호·예외 상세를 출력하지 않습니다.

환경변수를 사용하려면 `CAMPUSRENT_DB_USERNAME`, `CAMPUSRENT_DB_PASSWORD`를 설정합니다. 선택적으로 `CAMPUSRENT_DB_URL`도 지원합니다. 환경변수는 파일보다 우선하며 빈 환경변수도 파일 값을 덮어씁니다. `.env` 파일 자동 읽기는 구현하지 않았습니다. 현재는 Spring Boot가 없으므로 테스트 도구가 `spring.datasource.*` 키를 직접 읽습니다.

로컬 파일을 다시 만들 때는 `application-local.example.properties`를 복사합니다. 예시에는 실제 계정·비밀번호를 넣지 않습니다. Git은 로컬 설정 파일·`.env`·`.env.*`·`.work/`를 제외하고, 자격정보가 빈 예시 파일만 포함할 수 있습니다.

## 실행

저장소 루트에서 드라이버를 한 번 준비합니다. Maven Central에서 Oracle가 배포한 드라이버를 Git 제외 경로인 `.work/jdbc/`에 내려받으며 Maven 설치는 필요하지 않습니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/setup-oracle-jdbc.ps1
```

설정·네트워크·SELECT 확인 명령은 다음과 같습니다.

```powershell
.\check-oracle.cmd --check-config
.\check-oracle.cmd --tcp
.\check-oracle.cmd
```

- `--check-config`: 설정값 존재·URL 형식만 확인합니다. 네트워크에 접속하지 않고 값도 출력하지 않습니다.
- `--tcp`: 지정한 포트의 TCP 연결만 확인합니다. Oracle 인증이나 SQL을 실행하지 않습니다.
- 기본 실행: 드라이버를 로드해 로그인한 뒤 **고정된 `SELECT 1 FROM DUAL` 한 문장만 실행**하고 연결을 닫습니다. 계정이 비어 있으면 로그인도 시도하지 않습니다.

연결·읽기·조회 시간 제한을 설정했습니다. SQL 파일을 입력받거나 임의 SQL을 실행하는 기능은 없고, 기존 설치·검증 SQL에도 연결하지 않습니다. 결과가 `QUERY_PASS: SELECT 1 FROM DUAL returned 1.`이면 해당 SELECT 시험 성공입니다. 실패 시 비밀번호가 아닌 Oracle 오류 번호만 출력합니다.

## 연결 시험 및 2026-11-16 현재 상태

- 현재 Java 8에서 테스트 도구 컴파일 확인.
- JDBC 드라이버 준비 완료.
- 제공 호스트·포트 TCP 연결 성공. Oracle 프로토콜·SID·인증 성공을 의미하지 않습니다.
- 최초 인증 시험은 `ORA-01017`로 실패했으나 사용자가 로컬 설정을 수정한 뒤 재시험에 성공했습니다. `SELECT 1 FROM DUAL`이 `1`을 반환했습니다.
- 2026-11-16 사용자 요청에 따라 테이블 26개·시퀀스 17개와 관련 인덱스 생성을 완료했습니다. 컬럼 185개, PK 26개·FK 44개, 전체 인덱스 72개를 확인했습니다. 무효 인덱스·비활성 또는 미검증 제약조건은 없습니다.
- 초기 데이터는 0건이며, 승인 프로시저는 아직 생성하지 않았습니다. 상세 경과는 `docs/worklogs/2026-11-16-session.md`에 기록했습니다.

참고: [Oracle JDBC·JDK 호환성](https://www.oracle.com/database/technologies/faq-jdbc.html), [Oracle의 Maven Central 배포 안내](https://blogs.oracle.com/developers/oracle-database-client-libraries-for-java-now-on-maven-central), [Oracle SID URL 예시](https://github.com/oracle-samples/oracle-db-examples/blob/main/java/jdbc/BasicSamples/JDBCUrlSample.java).
