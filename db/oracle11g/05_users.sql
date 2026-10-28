-- PUBLIC DEVELOPMENT ACCOUNTS ONLY. Do not deploy these known passwords to production.
-- Salt(16 bytes) + PBKDF2-HMAC-SHA256(310000 iterations, 256 bits), hex; Spring Security v5_8 format.
-- No plaintext passwords are stored in APP_USER. See README for the public fixture logins.
INSERT INTO APP_USER (USER_ID,EMAIL,PASSWORD,USER_NAME,ROLE_CODE) VALUES (1,'admin@campusrent.example','{pbkdf2@SpringSecurity_v5_8}8c5f591d109d120b7246ad986452126fd49765c57bc91b63ed94c9ccc824af72015056bf2137931b8446a0b0a2e5f898','시연 관리자','ADMIN');
INSERT INTO APP_USER (USER_ID,EMAIL,PASSWORD,USER_NAME,ROLE_CODE) VALUES (2,'demo@campusrent.example','{pbkdf2@SpringSecurity_v5_8}6927401fb3072ba57820d945677c96bdf2d457deb911286578d468cc81e7b7ed7488982ba9c110b11a8e1238359ce657','시연 사용자','STUDENT');
INSERT INTO APP_USER (USER_ID,EMAIL,PASSWORD,USER_NAME,ROLE_CODE) VALUES (3,'other@campusrent.example','{pbkdf2@SpringSecurity_v5_8}7c02c96eac3fa329885e18b30b41a421378d45325304e72c2be9feceaa55f831b2139e5d5dc06530925884b21f963c3b','다른 사용자','STUDENT');
