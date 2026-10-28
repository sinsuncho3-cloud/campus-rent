-- REFERENCE QUERIES: run ONE named section with bound parameters, not this whole file.
-- JDBC/MyBatis: bind TIMESTAMP values, never concatenate dates or user input.
-- Every :actor_user_id must come from the authenticated server session, not a request form.

-- Q01 Login: lookup only. PasswordEncoder.matches(rawPassword, PASSWORD) runs in Java.
-- Never use WHERE PASSWORD = :plaintext, and never return PASSWORD to the browser.
SELECT USER_ID, EMAIL, PASSWORD, USER_NAME, ROLE_CODE
FROM APP_USER
WHERE EMAIL = LOWER(TRIM(:email));

-- Q02 Signup: role is always STUDENT; generate the password hash in Java.
INSERT INTO APP_USER (USER_ID,EMAIL,PASSWORD,USER_NAME,ROLE_CODE)
VALUES (SQ_APP_USER.NEXTVAL,LOWER(TRIM(:email)),:password_hash,:user_name,'STUDENT');

-- Q03 Equipment list/search. % and _ are wildcards; escape them in the service for literal search.
SELECT E.EQUIPMENT_ID,E.ASSET_CODE,E.EQUIPMENT_NAME,E.STORAGE_LOCATION,
       E.LEND_ALLOWED_YN,E.OPERATING_STATUS,E.ROW_VERSION,
       M.MODEL_CODE,M.MODEL_NAME,T.TYPE_NAME,T.CATEGORY_LABEL,E.IS_DEMO_YN
FROM EQUIPMENT E
JOIN EQUIPMENT_MODEL M ON M.MODEL_ID=E.MODEL_ID
JOIN EQUIPMENT_TYPE T ON T.TYPE_ID=M.TYPE_ID
WHERE T.ENABLED_YN='Y'
  AND (:type_id IS NULL OR T.TYPE_ID=:type_id)
  AND (:keyword IS NULL OR LOWER(E.EQUIPMENT_NAME||' '||E.ASSET_CODE||' '||M.MODEL_CODE) LIKE '%'||LOWER(:keyword)||'%')
ORDER BY E.EQUIPMENT_ID;

-- Q04 Specifications and components (two result sets).
SELECT S.SPEC_NAME,MS.SPEC_VALUE,MS.VALUE_UNIT,MS.VERIFY_STATUS
FROM EQUIPMENT E JOIN MODEL_SPEC MS ON MS.MODEL_ID=E.MODEL_ID
JOIN SPEC_ITEM S ON S.SPEC_ITEM_ID=MS.SPEC_ITEM_ID
WHERE E.EQUIPMENT_ID=:equipment_id ORDER BY S.DISPLAY_ORDER;
SELECT COMPONENT_ID,COMPONENT_NAME,REQUIRED_QTY,BODY_YN,REQUIRED_YN,DISPLAY_ORDER
FROM EQUIPMENT_COMPONENT WHERE EQUIPMENT_ID=:equipment_id ORDER BY DISPLAY_ORDER;

-- Q05 Facility candidates satisfying capacity, purpose, operating hours, blocks and reservations.
-- Validate end>start and integer people>=1 before issuing this query.
SELECT F.*
FROM FACILITY F
JOIN FACILITY_PURPOSE FP ON FP.FACILITY_ID=F.FACILITY_ID AND FP.PURPOSE_ID=:purpose_id
JOIN USE_PURPOSE P ON P.PURPOSE_ID=FP.PURPOSE_ID AND P.ENABLED_YN='Y'
JOIN FACILITY_HOURS H ON H.FACILITY_ID=F.FACILITY_ID
 AND H.WEEKDAY_NO=TRUNC(CAST(:use_start_at AS DATE))-TRUNC(CAST(:use_start_at AS DATE),'IW')+1
WHERE F.USE_ALLOWED_YN='Y' AND F.OPERATING_STATUS='NORMAL' AND F.CAPACITY>=:people_count
  AND :people_count>=1 AND :use_end_at>:use_start_at AND :use_start_at>LOCALTIMESTAMP
  AND TRUNC(CAST(:use_start_at AS DATE))=TRUNC(CAST(:use_end_at AS DATE))
  AND TO_CHAR(:use_start_at,'HH24:MI:SS.FF6')>=H.OPEN_TIME||':00.000000'
  AND TO_CHAR(:use_end_at,'HH24:MI:SS.FF6')<=H.CLOSE_TIME||':00.000000'
  AND NOT EXISTS (SELECT 1 FROM FACILITY_BLOCK B WHERE B.FACILITY_ID=F.FACILITY_ID
    AND B.BLOCK_START_AT<:use_end_at AND B.BLOCK_END_AT>:use_start_at)
  AND NOT EXISTS (SELECT 1 FROM REQUEST_ITEM R WHERE R.FACILITY_ID=F.FACILITY_ID
    AND R.STATUS_CODE IN ('APPROVED','IN_USE') AND R.USE_START_AT<:use_end_at AND R.USE_END_AT>:use_start_at)
ORDER BY F.CAPACITY,F.FACILITY_ID;

-- Q06 Equipment availability: pending requests do not reserve time. Half-open intervals [start,end).
SELECT E.EQUIPMENT_ID
FROM EQUIPMENT E JOIN EQUIPMENT_MODEL M ON M.MODEL_ID=E.MODEL_ID
JOIN EQUIPMENT_TYPE T ON T.TYPE_ID=M.TYPE_ID
WHERE E.EQUIPMENT_ID=:equipment_id AND T.ENABLED_YN='Y'
  AND E.LEND_ALLOWED_YN='Y' AND E.OPERATING_STATUS='NORMAL'
  AND :use_end_at>:use_start_at AND :use_start_at>LOCALTIMESTAMP
  AND NOT EXISTS (SELECT 1 FROM REQUEST_ITEM R WHERE R.EQUIPMENT_ID=E.EQUIPMENT_ID
    AND R.STATUS_CODE IN ('APPROVED','CHECKED_OUT','RETURN_REQUESTED','RETURN_HOLD')
    AND R.USE_START_AT<:use_end_at AND R.USE_END_AT>:use_start_at);

-- Q07 Directed equipment alternatives. Reapply Q06 to candidates before selecting.
SELECT A.TARGET_EQUIPMENT_ID,E.EQUIPMENT_NAME,A.REASON_TEXT,A.DIFFERENCE_TEXT
FROM EQUIPMENT_ALTERNATIVE A JOIN EQUIPMENT E ON E.EQUIPMENT_ID=A.TARGET_EQUIPMENT_ID
WHERE A.SOURCE_EQUIPMENT_ID=:equipment_id AND A.ENABLED_YN='Y'
  AND E.OPERATING_STATUS='NORMAL' AND E.LEND_ALLOWED_YN='Y';

-- Q08 Purpose rules. Keyword matching alone is NOT full UI activity analysis.
SELECT T.TYPE_ID,T.TYPE_NAME,R.NEED_LEVEL,R.REASON_TEXT
FROM PURPOSE_EQUIPMENT_RULE R JOIN EQUIPMENT_TYPE T ON T.TYPE_ID=R.TYPE_ID
JOIN USE_PURPOSE P ON P.PURPOSE_ID=R.PURPOSE_ID
WHERE R.PURPOSE_ID=:purpose_id AND T.ENABLED_YN='Y' AND P.ENABLED_YN='Y'
ORDER BY R.DISPLAY_ORDER;
SELECT PURPOSE_ID,KEYWORD_TEXT,MATCH_MODE,EFFECT_CODE,PRIORITY_NO
FROM PURPOSE_KEYWORD WHERE ENABLED_YN='Y' ORDER BY PRIORITY_NO DESC,KEYWORD_ID;

-- Q09 Submit: ONE transaction; obtain IDs first, bind all values, validate every item and role in service.
SELECT SQ_RENT_REQUEST.NEXTVAL AS REQUEST_ID FROM DUAL;
INSERT INTO RENT_REQUEST (REQUEST_ID,REQUEST_NO,APPLICANT_ID)
SELECT :request_id,'CR-'||TO_CHAR(LOCALTIMESTAMP,'YYYYMMDD')||'-'||TO_CHAR(:request_id),USER_ID
FROM APP_USER WHERE USER_ID=:actor_user_id AND ROLE_CODE='STUDENT';
-- Assert inserted row count=1; otherwise roll back. Do not trust an arbitrary request_id for the next insert.
SELECT SQ_REQUEST_ITEM.NEXTVAL AS REQUEST_ITEM_ID FROM DUAL;
INSERT INTO REQUEST_ITEM (REQUEST_ITEM_ID,REQUEST_ID,ITEM_NO,ITEM_KIND,EQUIPMENT_ID,FACILITY_ID,
 USE_START_AT,USE_END_AT,PEOPLE_COUNT,PURPOSE_ID,ORIGIN_CODE,ACTIVITY_TEXT)
SELECT :request_item_id,R.REQUEST_ID,:item_no,:item_kind,:equipment_id,:facility_id,
 :use_start_at,:use_end_at,:people_count,:purpose_id,:origin_code,:activity_text
FROM RENT_REQUEST R WHERE R.REQUEST_ID=:request_id AND R.APPLICANT_ID=:actor_user_id;
INSERT INTO REQUEST_STATUS_LOG (STATUS_LOG_ID,REQUEST_ITEM_ID,TO_STATUS,CHANGED_BY)
VALUES (SQ_REQUEST_STATUS_LOG.NEXTVAL,:request_item_id,'PENDING',:actor_user_id);
-- Delete only the submitted owner's SELECTION_ITEM rows after all inserts succeed, then COMMIT.

-- Q10 Student history, owner-scoped. Apply STATUS_CODE filters in service for current/past tabs.
SELECT R.REQUEST_NO,R.REQUESTED_AT,I.*,E.EQUIPMENT_NAME,F.FACILITY_NAME
FROM RENT_REQUEST R JOIN REQUEST_ITEM I ON I.REQUEST_ID=R.REQUEST_ID
LEFT JOIN EQUIPMENT E ON E.EQUIPMENT_ID=I.EQUIPMENT_ID
LEFT JOIN FACILITY F ON F.FACILITY_ID=I.FACILITY_ID
WHERE R.APPLICANT_ID=:actor_user_id ORDER BY R.REQUESTED_AT DESC,I.ITEM_NO;

-- Q11 Administrator queue. The server must require an ADMIN session.
SELECT R.REQUEST_NO,U.USER_NAME,I.*
FROM REQUEST_ITEM I JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID
JOIN APP_USER U ON U.USER_ID=R.APPLICANT_ID
WHERE (:status_code IS NULL OR I.STATUS_CODE=:status_code)
  AND EXISTS (SELECT 1 FROM APP_USER A WHERE A.USER_ID=:actor_user_id AND A.ROLE_CODE='ADMIN')
ORDER BY R.REQUESTED_AT,I.ITEM_NO;

-- Q12 Approve: installed procedure handles locks, latest conditions, version and log atomically.
-- Caller must COMMIT on success / ROLLBACK on exception. No COMMIT occurs inside the procedure.
BEGIN
  APPROVE_REQUEST_ITEM(:request_item_id,:actor_user_id,:expected_version);
END;
/

-- Q13 Cancel or return request. ONE transaction: execute guarded update, assert SQL%ROWCOUNT=1,
-- then insert the log with the verified old/new states; otherwise roll back.
-- :from_status/:to_status only accept PENDING->CANCELLED or CHECKED_OUT->RETURN_REQUESTED in service.
UPDATE REQUEST_ITEM I SET STATUS_CODE=:to_status,ROW_VERSION=ROW_VERSION+1,UPDATED_AT=LOCALTIMESTAMP
WHERE I.REQUEST_ITEM_ID=:request_item_id AND I.ROW_VERSION=:expected_version
 AND I.STATUS_CODE=:from_status
 AND ((:from_status='PENDING' AND :to_status='CANCELLED') OR
      (I.ITEM_KIND='E' AND :from_status='CHECKED_OUT' AND :to_status='RETURN_REQUESTED'))
 AND EXISTS (SELECT 1 FROM RENT_REQUEST R JOIN APP_USER U ON U.USER_ID=R.APPLICANT_ID
   WHERE R.REQUEST_ID=I.REQUEST_ID AND R.APPLICANT_ID=:actor_user_id AND U.ROLE_CODE='STUDENT');
INSERT INTO REQUEST_STATUS_LOG (STATUS_LOG_ID,REQUEST_ITEM_ID,FROM_STATUS,TO_STATUS,CHANGED_BY)
VALUES (SQ_REQUEST_STATUS_LOG.NEXTVAL,:request_item_id,:from_status,:to_status,:actor_user_id);

-- Q14 Cumulative return; preserve delivered snapshots even when default components change.
SELECT C.CHECKOUT_ID,C.COMPONENT_SEQ,C.COMPONENT_NAME,C.DELIVERED_QTY,C.REQUIRED_YN,
 NVL(SUM(R.RETURNED_QTY),0) RETURNED_TOTAL,
 C.DELIVERED_QTY-NVL(SUM(R.RETURNED_QTY),0) MISSING_QTY
FROM CHECKOUT_COMPONENT C LEFT JOIN RETURN_COMPONENT R
 ON R.CHECKOUT_ID=C.CHECKOUT_ID AND R.COMPONENT_SEQ=C.COMPONENT_SEQ
WHERE C.CHECKOUT_ID=:checkout_id
GROUP BY C.CHECKOUT_ID,C.COMPONENT_SEQ,C.COMPONENT_NAME,C.DELIVERED_QTY,C.REQUIRED_YN
ORDER BY C.COMPONENT_SEQ;

-- Q15 Lock strategy for checkout/return/facility use (reference, not a full workflow implementation):
-- 1. Lock EQUIPMENT/FACILITY row, then REQUEST_ITEM, then CHECKOUT/USAGE in that order.
-- 2. Check authenticated actor role, expected version and allowed transition.
-- 3. Checkout: ensure no other unresolved checkout on this equipment, INSERT EQUIPMENT_CHECKOUT,
--    copy EQUIPMENT_COMPONENT into CHECKOUT_COMPONENT (snapshots), update item+version, insert status log.
-- 4. Return: allocate round under checkout lock, INSERT RETURN_INSPECTION + RETURN_COMPONENT;
--    reject SUM(RETURNED_QTY)>DELIVERED_QTY, reject COMPLETE if required components are still missing.
--    Update item+version and optional EQUIPMENT_CHECK_LOG/EQUIPMENT state in the same transaction.
-- 5. Facility start/end: INSERT/UPDATE FACILITY_USAGE plus item+version+status log in the same transaction.
-- 6. COMMIT only after all steps succeed; ROLLBACK the entire transaction on any failure.
-- ALL writers, including facility blocks and manual status edits, must use the same resource-row locks.
