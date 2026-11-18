# 요구사항·구현 대응표

기준: 계획서 `New-Era_PROJECT.md`의 F01~F29, 2026-11-18 로컬 소스 및 Oracle 검증. 구현 표시는 전체 운영 검수·교수님 승인·사용자 효과 검증과 구분합니다. 원본 화면설계 PDF는 수정하지 않았으며 아래 경로는 현재 실행 화면입니다.

| ID | 화면 | 주요 서버·DB 대응 | 상태·남은 범위 |
| --- | --- | --- | --- |
| F01 | 공통 로그인·가입 창 | session/login/signup, APP_USER | 구현·시험. 학교 인증·복구·시도 제한 정책 별도 |
| F02 | /equipment | catalog, EQUIPMENT·TYPE·MODEL | 구현. 실제 학교 자산 확인 필요 |
| F03 | 장비 상세 창 | MODEL_SPEC·COMPONENT | 구현. 실제 사양 검증 필요 |
| F04 | 선택 목록→최종 확인 | submit, RENT_REQUEST·REQUEST_ITEM·STATUS_LOG | 구현·시험. 승인 전 미확보 |
| F05 | /records | transition 취소, 상태 이력 | 구현·소유권 검사 |
| F06 | /records | state, REQUEST_ITEM | 구현 |
| F07 | /records | RETURN_REQUESTED 전이 | 구현·중복 차단 |
| F08 | /admin/models, /admin/equipment | ModelService·AdminService, TYPE·MODEL·SPEC·EQUIPMENT·COMPONENT | 품목·모델·사양·자산 편집 구현. 실물 정보 확인 필요 |
| F09 | /admin | transition, 자원 잠금·버전·상태 이력 | 구현·동시 승인 검증 |
| F10 | 관리자 반납 검수 창 | RETURN_INSPECTION·RETURN_COMPONENT | 구성품별 수량·정상/손상/미반납·메모 저장 구현. 손상 시 점검 전환 |
| F11 | /history, /admin, 출고·검수 회차 상세 창 | inspection-history, 출고 기준·누적 반환·회차 이력 | 출고 당시 구성품과 회차별 수량·누적·상태·메모·처리자·시각 조회 구현 |
| F12 | /admin, /admin/equipment | CHECKOUT·CHECKOUT_COMPONENT·CHECK_LOG | 실제 출고·점검·사후 출고 등록 구현. 사후 등록은 승인된 장비 신청에 한함 |
| F13 | 없음 | 통계 집계 API 없음 | 선택 기능·미구현 |
| F14 | 없음 | 기본 미반납 제한은 전이에서 검사 | 별도 연체 목록은 선택·미구현 |
| F15 | 없음 | 상태는 내역에서 조회 | 별도 알림은 선택·미구현 |
| F16 | 다른 일정 조회 창 | alternateSlots, 제출 시 available 재검증 | 구현. 최대 31일 검색, 같은 대여 길이 |
| F17 | 대체 조회, /admin/alternatives | EQUIPMENT_ALTERNATIVE·REQUEST_ALTERNATIVE | 방향별 관계 편집·해제·조회·선택 출처 저장 구현 |
| F18 | 없음 | 대기열 API 없음 | 선택 기능·미구현 |
| F19 | /facilities, 상세 창 | FACILITY·HOURS·PURPOSE | 구현. 실제 시설 운영 조건 확인 필요 |
| F20 | 선택 목록, /records | submit/transition, 자원 조건·소유권 검사 | 구현·시험 |
| F21 | /admin/facilities | FACILITY·HOURS·PURPOSE·BLOCK·TBL_FACILITY_BLOCK_LOG | 등록·수정·제한 해제·감사 이력 구현. 기존 승인 충돌 차단 |
| F22 | /admin | 시설 승인·거절, 잠금·버전 | 구현 |
| F23 | /admin | FACILITY_USAGE 실제 시작·종료 | 구현 |
| F24 | /records, /history, /admin | state, 상태·처리자·시각 | 구현 |
| F25 | 다른 일정·대체 시설 조회 창 | alternateSlots/alternateResources, available 재검증 | 구현. 최초 희망 자원·기간 보존 |
| F26 | 없음 | 통계 집계 API 없음 | 선택 기능·미구현 |
| F27 | /purpose, /admin/recommendations | Oracle 목적·키워드·장비 규칙, 문맥 검사, ACTIVITY_TEXT·ORIGIN_CODE 저장 | 확장 목적·키워드 편집 구현. 전체 목적 정의·장비 규칙 편집 UI와 실사용 효과 수치 검증은 별도 |
| F28 | 선택 목록·조건 수정 창 | sessionStorage, submit 시 최종 저장 | 구현. SELECTION_ITEM을 통한 서버 장바구니는 사용하지 않음 |
| F29 | 관리자 추가 반환 검수 창·회차 상세 | 동일 CHECKOUT의 회차·누적량 검사 | 구현·시험. 이전 회차의 손상·메모 보존, 누적량·처리자·시각 조회 |

## 이번 변경의 DB 반영

기존 26개 테이블·SQ_ 시퀀스 이름은 유지합니다. `10_block_audit.sql`로 FACILITY_BLOCK에 해제 관련 컬럼 4개를 추가하고 `TBL_FACILITY_BLOCK_LOG`, `SEQ_FACILITY_BLOCK_LOG`를 추가했습니다. 원본 ERD 이후의 확장이므로 ERD 개정본에 반영할 대상입니다. 기존 제한의 과거 변경 이력은 재구성하지 않고 IMPORT 기준 기록만 남깁니다. 09번 검증은 원본 기준이며 증분은 11번으로 확인합니다.

## 다음 구현·검증 순서

1. F10·F11·F12의 손상·메모·회차 상세·사후 출고를 구현했습니다. 실제 현장 구성품·손상 판정·사후 등록 허용 기간 정책은 팀 검토가 필요합니다. 사후 등록은 승인 신청에 당시 전달 수량·상태·실제 시각·사유를 입력하며 기존 실제 대여 기록과 겹치는 시각은 차단합니다.
2. [사용자 과업 시험](usability-test.md)을 실제 참여자와 진행하고 결과·개선·재시험을 기록합니다.
3. [배포 절차](deployment.md)에 따라 서버 환경·HTTPS·운영 계정·보안 정책을 확정하고 배포합니다. 현재는 패키지·설정 예시 준비 단계입니다.
4. 선택 기능은 필수 잔여 요구와 운영 검증 후 우선순위를 정합니다. Git Commit/Push는 별도 승인 후 진행합니다.
