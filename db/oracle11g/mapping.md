# ERD·화면·DB 매핑

기준: `docs/erd/[New-Era] CampusRent_ERD_20261020.pdf`의 물리 ERD(2쪽), `src/main/resources/static/js/data.js`, `app.js`, `store.js`. ERD의 **26개 테이블·185개 컬럼과 자료형·NULL 허용 여부를 유지**했습니다. 테이블·컬럼을 추가하거나 이름을 바꾸지 않았습니다.

## 화면과 저장 대상

| 화면/기능 | 테이블 | 핵심 매핑 |
| --- | --- | --- |
| 로그인·회원가입 | `APP_USER` | `EMAIL` 로그인, `PASSWORD` 해시, `ROLE_CODE` 학생·관리자 |
| 장비 분류 | `EQUIPMENT_TYPE` | `TYPE_NAME`, `CATEGORY_LABEL`, 사용 여부 |
| 장비 모델 | `EQUIPMENT_MODEL` | 모델 코드·모델명·가상 여부 |
| 개별 장비 목록·상세 | `EQUIPMENT` | 관리번호·이름·장소·대여 허용·운영 상태·변경 버전 |
| 장비 기본 구성품 | `EQUIPMENT_COMPONENT` | 구성품명·필수 수량·본체·필수 반환·순서 |
| 사양 정의 | `SPEC_ITEM` | 품목별 사양명·단위·순서 |
| 모델 사양 | `MODEL_SPEC` | 모델별 사양값·단위·확인 상태 |
| 같은 일정의 대체 장비 | `EQUIPMENT_ALTERNATIVE` | 원래→대체 방향, 이유·차이·설정 관리자 |
| 시설 목록·상세 | `FACILITY` | 코드·이름·분류·위치·수용 인원·비치 설비 |
| 시설 운영 시간 | `FACILITY_HOURS` | ISO 요일(월=1~일=7), HH24:MI 시작·종료 |
| 시설 허용 용도 | `FACILITY_PURPOSE` | 시설과 이용 목적의 다대다 관계 |
| 시설 점검·운영 제한 | `FACILITY_BLOCK` | 제한 시작·종료·사유·등록 관리자 |
| 활동 목적·설명 | `USE_PURPOSE` | 인터뷰·발표·회의·과제 영상 4개 목적 |
| 목적 인식 키워드 | `PURPOSE_KEYWORD` | 포함/제외·일치 방식·우선순위 |
| 목적별 장비 구성 | `PURPOSE_EQUIPMENT_RULE` | 목적·품목별 필수/선택·추천 이유 |
| 로그인 후 선택 목록 | `SELECTION_ITEM` | 사용자·자원·일정·인원·용도·선택 출처 |
| 최종 제출 | `RENT_REQUEST` | 접수 번호·신청 학생·접수 시각 |
| 신청 항목·학생 현황 | `REQUEST_ITEM` | 묶음 내 원래 순번·자원·조건·상태·버전 |
| 대체안 선택 출처 | `REQUEST_ALTERNATIVE` | 최초 희망 자원·조건, 다른 일정/다른 자원 선택 |
| 승인·거절·취소·반납 요청 | `REQUEST_STATUS_LOG` | 이전/다음 상태·처리자·시각·사유 |
| 실제 장비 출고 | `EQUIPMENT_CHECKOUT` | 관리번호·장비명 스냅샷, 실제/사후 기록 구분 |
| 출고 구성품 검수 | `CHECKOUT_COMPONENT` | 출고 시점 구성품명·기준/전달 수량·상태 보존 |
| 반납·추가 반환 회차 | `RETURN_INSPECTION` | 출고별 회차·확인자·완료/보류·사유 |
| 회차별 반환 수량 | `RETURN_COMPONENT` | 이번 반환 수량·상태; 누적은 회차 합계로 계산 |
| 장비 점검 | `EQUIPMENT_CHECK_LOG` | 이전/다음 운영 상태·점검 사유·담당자 |
| 시설 실제 이용 | `FACILITY_USAGE` | 실제 시작/종료 시각·확인 관리자·상태 메모 |

비로그인 선택은 현재처럼 브라우저에 보존하고 로그인 성공만으로 신청을 만들지 않습니다. 서버 선택 목록 저장 여부는 연동 단계에서 확정합니다. 다른 일정 검색 결과 자체는 저장하지 않고, 최종 신청에 반영된 최초 조건만 `REQUEST_ALTERNATIVE`에 남깁니다.

## 브라우저 ID와 초기 DB ID

| 브라우저 ID | DB 테이블·ID | 코드 |
| --- | --- | --- |
| cam01 / cam02 | EQUIPMENT 1 / 2 | DEMO-CAM01 / DEMO-CAM02 |
| proj01 / proj02 | EQUIPMENT 3 / 4 | DEMO-PROJ01 / DEMO-PROJ02 |
| mic01 / mic02 / mic03 | EQUIPMENT 5 / 6 / 7 | DEMO-MIC01 / 02 / 03 |
| room01 / room02 | FACILITY 1 / 2 | DEMO-ROOM01 / DEMO-ROOM02 |
| study01 | FACILITY 3 | DEMO-STUDY01 |
| studio01 / studio02 | FACILITY 4 / 5 | DEMO-STUDIO01 / DEMO-STUDIO02 |
| 인터뷰 촬영 | USE_PURPOSE 1 | INTERVIEW |
| 발표 연습 | USE_PURPOSE 2 | PRESENTATION |
| 팀 회의 | USE_PURPOSE 3 | MEETING |
| 과제 영상 촬영 | USE_PURPOSE 4 | ASSIGNMENT_VIDEO |

초기 관리자 ID=1, 학생 ID=2·3입니다. 신규 데이터는 1000부터 시퀀스로 발급합니다. 화면의 UUID를 숫자로 변환하지 말고, 서버 연동 후 API에서 반환한 DB ID를 사용하세요.

## 상태 코드

| 화면 문구 | REQUEST_ITEM.STATUS_CODE |
| --- | --- |
| 승인 대기 | PENDING |
| 승인 | APPROVED |
| 거절 | REJECTED |
| 취소 | CANCELLED |
| 출고 | CHECKED_OUT |
| 반납 요청 | RETURN_REQUESTED |
| 반납 보류 | RETURN_HOLD |
| 반납 완료 | RETURNED |
| 이용 중 | IN_USE |
| 이용 완료 | COMPLETED |

허용 흐름: `PENDING → APPROVED / REJECTED / CANCELLED`, 장비 `APPROVED → CHECKED_OUT → RETURN_REQUESTED → RETURN_HOLD / RETURNED`, 보류 `RETURN_HOLD → RETURN_HOLD / RETURNED`, 시설 `APPROVED → IN_USE → COMPLETED`.

기타 코드:

- 역할: `STUDENT`, `ADMIN`; 자원 유형: `E`, `F`.
- 자원 운영 상태: `NORMAL`, `CHECKING`, `STOPPED`.
- 선택 출처: `DIRECT`, `PURPOSE`, `ALTERNATE`.
- 사양 확인: `CONFIRMED`, `PENDING`; 추천 필요 구분: `REQUIRED`, `OPTIONAL`.
- 키워드 일치: `CONTAINS`, `EXACT`; 효과: `INCLUDE`, `EXCLUDE`.
- 대체 유형: `OTHER_TIME`, `OTHER_RESOURCE`.
- 출고 출처: `ACTUAL`, `BACKFILL`; 반납 결과: `COMPLETE`, `HOLD`.
- 구성품 상태: `NORMAL`, `DAMAGED`, `MISSING`.
- 장비 점검 행동: `START_CHECK`, `END_CHECK`, `STOP`, `RESUME`.

이 값들은 ERD에 정의되지 않은 부분을 화면에 맞춰 정한 **구현 코드 규약**입니다. 테이블을 늘리지 않고 CHECK 제약조건으로 제한했습니다. 코드 변경 시 SQL·서버·화면을 함께 수정해야 합니다.

## ERD에 보완한 제약조건과 연동 규칙

- 장비/시설 중 하나만 지정하도록 CHECK를 추가했습니다. 시설 신청은 인원·목적을 필수로 검사합니다. 종료는 시작보다 늦어야 합니다.
- 이메일은 소문자·공백 제거 형태와 유일 인덱스를 적용했습니다. 일반 회원가입은 서버에서 `STUDENT`로 고정합니다.
- `REQUEST_ITEM (REQUEST_ITEM_ID, ITEM_KIND)`에 복합 UNIQUE를 두고 출고·시설 실제 이용이 올바른 자원 종류만 참조하도록 복합 FK를 연결했습니다.
- 출고/시설 이용은 신청 항목당 한 건, 반납 확인은 출고당 여러 회차입니다. 기본 구성품이 바뀌어도 출고 스냅샷을 덮어쓰지 않습니다.
- `RETURN_COMPONENT`는 회차 FK와 출고 구성품 FK를 모두 가집니다. 초과 반환·미반납 완료는 행 간 합계가 필요하므로 서버 트랜잭션에서 검사합니다.
- 승인 절차 `APPROVE_REQUEST_ITEM`는 자원→신청 항목 순서로 잠금, 관리자·버전·상태·기간·시설 조건·예약 중복을 검사하고 상태 이력을 함께 저장합니다. COMMIT은 호출자가 수행합니다. 신청/자원 편집 등 다른 경로도 동일한 잠금 순서를 사용해야 합니다.
- DB FK는 사용자의 존재만 보장합니다. 관리자 역할, 본인 소유권, 상태 전이, 실제 출고 전 미반납 장비 여부는 서버에서 추가 확인해야 합니다. 승인 프로시저 외 전체 출고·반납 API를 구현한 상태는 아닙니다.
- ERD에는 별도 반납 요청 테이블이 없어 `RETURN_REQUESTED` 상태 이력의 처리자·시각으로 반납 신청을 보존합니다.
- 목적 키워드만으로 화면의 모든 문맥·부정 표현·참여 인원 해석을 대체할 수 없습니다. 서버에서 화면의 분석 규칙을 이식하고 모호한 목적은 사용자에게 확인해야 합니다.
- 시설 이용 시간·정원, 고정 세트 한 개 단위 대여, 가상 자원은 현 시연 기준입니다. 실제 학교 정책을 확정한 데이터로 바꿔야 합니다.
- `TIMESTAMP`는 시간대를 저장하지 않으므로 DB 세션은 `+09:00`으로 맞추고 `LOCALTIMESTAMP` 기준으로 사용합니다. JDBC 연결 풀에도 같은 세션 설정이 필요합니다.
