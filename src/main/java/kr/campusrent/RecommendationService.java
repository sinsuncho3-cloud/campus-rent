package kr.campusrent;
import com.fasterxml.jackson.databind.JsonNode;import java.text.Normalizer;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import static kr.campusrent.RentalService.*;
@Service
public class RecommendationService {
 final JdbcTemplate db;public RecommendationService(JdbcTemplate db){this.db=db;}
 @Transactional(readOnly=true)
 public Object rules(){return obj("purposes",db.queryForList("SELECT PURPOSE_ID,PURPOSE_CODE,PURPOSE_NAME,DESCRIPTION_TEXT FROM USE_PURPOSE WHERE ENABLED_YN='Y' ORDER BY PURPOSE_ID"),"keywords",db.queryForList("SELECT K.* FROM PURPOSE_KEYWORD K JOIN USE_PURPOSE P ON P.PURPOSE_ID=K.PURPOSE_ID WHERE P.ENABLED_YN='Y' AND K.ENABLED_YN='Y' ORDER BY K.PRIORITY_NO DESC,K.KEYWORD_ID"),"equipment",db.queryForList("SELECT R.*,T.CATEGORY_LABEL FROM PURPOSE_EQUIPMENT_RULE R JOIN EQUIPMENT_TYPE T ON T.TYPE_ID=R.TYPE_ID JOIN USE_PURPOSE P ON P.PURPOSE_ID=R.PURPOSE_ID WHERE P.ENABLED_YN='Y' AND T.ENABLED_YN='Y' ORDER BY R.DISPLAY_ORDER,T.TYPE_ID"));}
 @Transactional(readOnly=true)
 public Object management(Map<String,Object> user){admin(user);return obj("purposes",db.queryForList("SELECT * FROM USE_PURPOSE ORDER BY PURPOSE_ID"),"keywords",db.queryForList("SELECT * FROM PURPOSE_KEYWORD ORDER BY PURPOSE_ID,PRIORITY_NO DESC,KEYWORD_ID"));}
 String required(JsonNode input,String key,int max){String value=text(input,key,max);check(!value.isEmpty(),"필수 항목을 입력해주세요.");return value;}
 @Transactional
 public long save(Long id,JsonNode input,Map<String,Object> user){
  admin(user);long purpose=integer(input,"purposeId",1,9999999999L),priority=integer(input,"priority",0,999);String word=Normalizer.normalize(required(input,"keyword",100),Normalizer.Form.NFKC),mode=required(input,"mode",10),effect=required(input,"effect",10);check(Arrays.asList("CONTAINS","EXACT").contains(mode)&&Arrays.asList("INCLUDE","EXCLUDE").contains(effect)&&input.path("enabled").isBoolean(),"키워드 조건을 확인해주세요.");
  db.queryForMap("SELECT PURPOSE_ID FROM USE_PURPOSE WHERE PURPOSE_ID=? FOR UPDATE WAIT 5",purpose);String enabled=input.path("enabled").asBoolean()?"Y":"N";
  if(id==null){id=db.queryForObject("SELECT SQ_PURPOSE_KEYWORD.NEXTVAL FROM DUAL",Long.class);db.update("INSERT INTO PURPOSE_KEYWORD(KEYWORD_ID,PURPOSE_ID,KEYWORD_TEXT,MATCH_MODE,EFFECT_CODE,PRIORITY_NO,ENABLED_YN) VALUES(?,?,?,?,?,?,?)",id,purpose,word,mode,effect,priority,enabled);}
  else{Map<String,Object> old=db.queryForMap("SELECT * FROM PURPOSE_KEYWORD WHERE KEYWORD_ID=? FOR UPDATE WAIT 5",id);check(num(old,"PURPOSE_ID")==purpose,"기존 키워드의 목적은 변경할 수 없습니다.");JsonNode expected=input.path("expected");check(str(old,"KEYWORD_TEXT").equals(text(expected,"keyword",100))&&str(old,"MATCH_MODE").equals(text(expected,"mode",10))&&str(old,"EFFECT_CODE").equals(text(expected,"effect",10))&&num(old,"PRIORITY_NO")==expected.path("priority").asLong(-1)&&str(old,"ENABLED_YN").equals(expected.path("enabled").asBoolean()?"Y":"N"),"다른 창에서 수정되었습니다. 새로 조회해주세요.");db.update("UPDATE PURPOSE_KEYWORD SET KEYWORD_TEXT=?,MATCH_MODE=?,EFFECT_CODE=?,PRIORITY_NO=?,ENABLED_YN=? WHERE KEYWORD_ID=?",word,mode,effect,priority,enabled,id);}
  return id;
 }
}
