-- SQL*Plus / SQL Developer Run Script (F5). Connect to the intended EMPTY development schema first.
-- This installer includes PUBLIC demo accounts and resources. Never use for production.
WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK
SET DEFINE OFF
SET ECHO OFF
SET VERIFY OFF
SET SERVEROUTPUT ON
SET SQLBLANKLINES ON
ALTER SESSION SET TIME_ZONE = '+09:00';
@@00_preflight.sql
@@01_tables.sql
@@02_sequences.sql
@@03_indexes.sql
@@08_approve.sql
-- DDL commits implicitly. Check compilation BEFORE inserting fixtures.
DECLARE
  V_COUNT NUMBER;
BEGIN
  SELECT COUNT(*) INTO V_COUNT FROM USER_ERRORS WHERE NAME='APPROVE_REQUEST_ITEM';
  IF V_COUNT>0 THEN RAISE_APPLICATION_ERROR(-20103,'Approval procedure compilation failed; inspect USER_ERRORS.'); END IF;
END;
/
@@04_codes.sql
@@05_users.sql
@@06_demo_data.sql
@@09_verify.sql
COMMIT;
PROMPT CampusRent development schema installation and database checks completed.
-- No DROP, TRUNCATE, credential change, user creation or remote connection occurs in this installer.
