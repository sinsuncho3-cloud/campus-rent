package kr.campusrent;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static kr.campusrent.RentalService.*;

/** Master-data changes use the same parent-resource locks as request approval. No asset/history deletion. */
@Service
public class AdminService {
  final JdbcTemplate db;
  final RentalService rental;
  final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
  public AdminService(JdbcTemplate db,RentalService rental){this.db=db;this.rental=rental;}
  String required(JsonNode n,String key,int length){String s=text(n,key,length);check(!s.isEmpty(),"필수 항목을 입력해주세요.");return s;}
  String flag(JsonNode n,String key){check(n.path(key).isBoolean(),"사용 여부를 확인해주세요.");return n.path(key).asBoolean()?"Y":"N";}
  String operating(JsonNode n){String s=text(n,"status",10);check(Arrays.asList("NORMAL","CHECKING","STOPPED").contains(s),"운영 상태를 확인해주세요.");return s;}
  long next(String name){check(Arrays.asList("EQUIPMENT","EQUIPMENT_COMPONENT","FACILITY","FACILITY_BLOCK").contains(name),"잘못된 식별자입니다.");return db.queryForObject("SELECT SQ_"+name+".NEXTVAL FROM DUAL",Long.class);}
  void version(Map<String,Object> row,JsonNode input){check(num(row,"ROW_VERSION")==integer(input,"version",1,9999999999L),"다른 창에서 변경되었습니다. 새로 조회해주세요.");}
  @Transactional(readOnly=true)
  public Map<String,Object> snapshot(Map<String,Object> user){
    admin(user);
    return obj("equipment",db.queryForList("SELECT * FROM EQUIPMENT ORDER BY EQUIPMENT_ID"),"models",db.queryForList("SELECT M.*,T.TYPE_NAME FROM EQUIPMENT_MODEL M JOIN EQUIPMENT_TYPE T ON T.TYPE_ID=M.TYPE_ID WHERE T.ENABLED_YN='Y' ORDER BY M.MODEL_ID"),"components",db.queryForList("SELECT * FROM EQUIPMENT_COMPONENT ORDER BY EQUIPMENT_ID,DISPLAY_ORDER"),"alternatives",db.queryForList("SELECT * FROM EQUIPMENT_ALTERNATIVE ORDER BY SOURCE_EQUIPMENT_ID,TARGET_EQUIPMENT_ID"),"facilities",db.queryForList("SELECT * FROM FACILITY ORDER BY FACILITY_ID"),"hours",db.queryForList("SELECT * FROM FACILITY_HOURS ORDER BY FACILITY_ID,WEEKDAY_NO"),"purposes",db.queryForList("SELECT PURPOSE_ID,PURPOSE_NAME FROM USE_PURPOSE WHERE ENABLED_YN='Y' ORDER BY PURPOSE_ID"),"facilityPurposes",db.queryForList("SELECT * FROM FACILITY_PURPOSE ORDER BY FACILITY_ID,PURPOSE_ID"),"blocks",db.queryForList("SELECT * FROM FACILITY_BLOCK ORDER BY FACILITY_ID,BLOCK_START_AT"));
  }
  @Transactional
  public long saveEquipment(Long id,JsonNode input,Map<String,Object> user){
    admin(user);String asset=required(input,"code",30),name=required(input,"name",100),place=required(input,"location",200),department=text(input,"department",100),allowed=flag(input,"allowed"),status=operating(input),reason=text(input,"reason",1000);
    long model=integer(input,"modelId",1,9999999999L);
    check(db.queryForObject("SELECT COUNT(*) FROM EQUIPMENT_MODEL M JOIN EQUIPMENT_TYPE T ON T.TYPE_ID=M.TYPE_ID WHERE M.MODEL_ID=? AND T.ENABLED_YN='Y'",Integer.class,model)==1,"사용 가능한 모델을 선택해주세요.");
    JsonNode parts=input.path("components");check(parts.isArray()&&parts.size()>0&&parts.size()<=99,"구성품을 1~99개 등록해주세요.");
    Set<String> names=new HashSet<>();int bodies=0;
    for(JsonNode part:parts){check(names.add(required(part,"name",100)),"구성품 이름이 중복됩니다.");integer(part,"quantity",1,99999);flag(part,"body");flag(part,"required");if(part.path("body").asBoolean())bodies++;}
    check(bodies==1,"본체 구성품을 한 개 지정해주세요.");
    Map<String,Object> old=null;
    if(id!=null){
      old=rental.lockResource("E"+id);version(old,input);
      int active=db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM WHERE EQUIPMENT_ID=? AND ((STATUS_CODE='APPROVED' AND USE_END_AT>LOCALTIMESTAMP) OR STATUS_CODE IN ('CHECKED_OUT','RETURN_REQUESTED','RETURN_HOLD'))",Integer.class,id);
      check(active==0||("NORMAL".equals(status)&&"Y".equals(allowed)&&model==num(old,"MODEL_ID")),"승인·미반납 신청이 있어 모델 변경이나 이용 중지를 할 수 없습니다. 관련 신청을 먼저 처리해주세요.");
      if(!status.equals(old.get("OPERATING_STATUS")))check(!reason.isEmpty(),"운영 상태 변경 사유를 입력해주세요.");
      db.update("UPDATE EQUIPMENT SET MODEL_ID=?,ASSET_CODE=?,EQUIPMENT_NAME=?,STORAGE_LOCATION=?,DEPARTMENT_NAME=?,LEND_ALLOWED_YN=?,OPERATING_STATUS=?,ROW_VERSION=ROW_VERSION+1 WHERE EQUIPMENT_ID=?",model,asset,name,place,department,allowed,status,id);
    }else{
      id=next("EQUIPMENT");db.update("INSERT INTO EQUIPMENT(EQUIPMENT_ID,MODEL_ID,ASSET_CODE,EQUIPMENT_NAME,STORAGE_LOCATION,DEPARTMENT_NAME,LEND_ALLOWED_YN,OPERATING_STATUS,IS_DEMO_YN) VALUES(?,?,?,?,?,?,?,?,'Y')",id,model,asset,name,place,department,allowed,status);
    }
    // Default component definitions may change; already-issued checkout snapshots remain untouched.
    db.update("DELETE FROM EQUIPMENT_COMPONENT WHERE EQUIPMENT_ID=?",id);
    int order=0;for(JsonNode part:parts)db.update("INSERT INTO EQUIPMENT_COMPONENT(COMPONENT_ID,EQUIPMENT_ID,COMPONENT_NAME,REQUIRED_QTY,BODY_YN,REQUIRED_YN,DISPLAY_ORDER) VALUES(?,?,?,?,?,?,?)",next("EQUIPMENT_COMPONENT"),id,text(part,"name",100),part.path("quantity").asLong(),flag(part,"body"),flag(part,"required"),++order);
    String previous=old==null?"NORMAL":str(old,"OPERATING_STATUS");
    if(!previous.equals(status)){
      String action="CHECKING".equals(status)?"START_CHECK":"STOPPED".equals(status)?"STOP":"CHECKING".equals(previous)?"END_CHECK":"RESUME";
      db.update("INSERT INTO EQUIPMENT_CHECK_LOG(CHECK_LOG_ID,EQUIPMENT_ID,ACTION_CODE,FROM_STATUS,TO_STATUS,REASON_TEXT,CHECKED_BY) VALUES(?,?,?,?,?,?,?)",rental.next("EQUIPMENT_CHECK_LOG"),id,action,previous,status,reason.isEmpty()?"관리자 장비 등록":reason,userId(user));
    }
    return id;
  }
  @Transactional
  public void saveAlternative(JsonNode input,Map<String,Object> user){
    admin(user);long source=integer(input,"sourceId",1,9999999999L),target=integer(input,"targetId",1,9999999999L);check(source!=target,"자기 자신을 대체 장비로 지정할 수 없습니다.");
    for(String resource:new TreeSet<String>(Arrays.asList("E"+source,"E"+target)))rental.lockResource(resource);
    long a=db.queryForObject("SELECT M.TYPE_ID FROM EQUIPMENT E JOIN EQUIPMENT_MODEL M ON M.MODEL_ID=E.MODEL_ID WHERE E.EQUIPMENT_ID=?",Long.class,source),b=db.queryForObject("SELECT M.TYPE_ID FROM EQUIPMENT E JOIN EQUIPMENT_MODEL M ON M.MODEL_ID=E.MODEL_ID WHERE E.EQUIPMENT_ID=?",Long.class,target);
    check(a==b,"같은 품목의 장비끼리 대체 관계를 지정해주세요.");
    String reason=required(input,"reason",500),difference=text(input,"difference",1000),enabled=flag(input,"enabled");
    int exists=db.queryForObject("SELECT COUNT(*) FROM EQUIPMENT_ALTERNATIVE WHERE SOURCE_EQUIPMENT_ID=? AND TARGET_EQUIPMENT_ID=?",Integer.class,source,target);
    if(input.path("existing").asBoolean(false)){
      check(exists==1,"관계가 변경되었습니다. 새로 조회해주세요.");
      List<Map<String,Object>> rows=db.queryForList("SELECT REASON_TEXT,DIFFERENCE_TEXT,ENABLED_YN FROM EQUIPMENT_ALTERNATIVE WHERE SOURCE_EQUIPMENT_ID=? AND TARGET_EQUIPMENT_ID=?",source,target);
      Map<String,Object> old=rows.get(0);JsonNode expected=input.path("expected");
      check(str(old,"REASON_TEXT").equals(text(expected,"reason",500))&&str(old,"DIFFERENCE_TEXT").equals(text(expected,"difference",1000))&&str(old,"ENABLED_YN").equals(expected.path("enabled").asBoolean()?"Y":"N"),"다른 창에서 수정한 관계입니다. 새로 조회해주세요.");
      db.update("UPDATE EQUIPMENT_ALTERNATIVE SET REASON_TEXT=?,DIFFERENCE_TEXT=?,ENABLED_YN=?,CONFIGURED_BY=? WHERE SOURCE_EQUIPMENT_ID=? AND TARGET_EQUIPMENT_ID=?",reason,difference,enabled,userId(user),source,target);
    }else{
      check(exists==0,"이미 등록된 방향의 관계입니다. 기존 관계를 수정해주세요.");
      db.update("INSERT INTO EQUIPMENT_ALTERNATIVE(SOURCE_EQUIPMENT_ID,TARGET_EQUIPMENT_ID,REASON_TEXT,DIFFERENCE_TEXT,ENABLED_YN,CONFIGURED_BY) VALUES(?,?,?,?,?,?)",source,target,reason,difference,enabled,userId(user));
    }
  }
  Map<Integer,String[]> hours(JsonNode input){
    JsonNode rows=input.path("hours");check(rows.isArray()&&rows.size()<=7,"요일별 운영 시간을 확인해주세요.");Map<Integer,String[]> result=new HashMap<>();
    for(JsonNode row:rows){int day=(int)integer(row,"day",1,7);String open=required(row,"open",5),close=required(row,"close",5);check(open.matches("([01][0-9]|2[0-3]):[0-5][0-9]")&&close.matches("([01][0-9]|2[0-3]):[0-5][0-9]")&&open.compareTo(close)<0,"운영 시작·종료 시간을 확인해주세요.");check(!result.containsKey(day),"요일이 중복됩니다.");result.put(day,new String[]{open,close});}
    return result;
  }
  @Transactional
  public long saveFacility(Long id,JsonNode input,Map<String,Object> user){
    admin(user);String code=required(input,"code",30),name=required(input,"name",100),type=required(input,"type",20),place=required(input,"location",200),info=text(input,"info",1000),allowed=flag(input,"allowed"),status=operating(input);long capacity=integer(input,"capacity",1,999);
    Map<Integer,String[]> hours=hours(input);JsonNode purposeNodes=input.path("purposeIds");check(purposeNodes.isArray(),"허용 목적을 선택해주세요.");Set<Long> purposes=new HashSet<>();
    for(JsonNode value:purposeNodes){check(value.isIntegralNumber(),"목적을 확인해주세요.");long purpose=value.asLong();check(purposes.add(purpose)&&db.queryForObject("SELECT COUNT(*) FROM USE_PURPOSE WHERE PURPOSE_ID=? AND ENABLED_YN='Y'",Integer.class,purpose)==1,"허용 목적을 확인해주세요.");}
    check(!"Y".equals(allowed)||!purposes.isEmpty()&&!hours.isEmpty(),"이용 허용 시설에는 목적과 운영 요일이 필요합니다.");
    if(id!=null){
      Map<String,Object> old=rental.lockResource("F"+id);version(old,input);
      List<Map<String,Object>> reservations=db.queryForList("SELECT * FROM REQUEST_ITEM WHERE FACILITY_ID=? AND ((STATUS_CODE='APPROVED' AND USE_END_AT>LOCALTIMESTAMP) OR STATUS_CODE='IN_USE')",id);
      for(Map<String,Object> r:reservations){
        LocalDateTime start=((Timestamp)r.get("USE_START_AT")).toLocalDateTime(),end=((Timestamp)r.get("USE_END_AT")).toLocalDateTime();String[] h=hours.get(start.getDayOfWeek().getValue());
        boolean fits="Y".equals(allowed)&&"NORMAL".equals(status)&&num(r,"PEOPLE_COUNT")<=capacity&&purposes.contains(num(r,"PURPOSE_ID"))&&h!=null&&start.toLocalDate().equals(end.toLocalDate())&&!start.toLocalTime().isBefore(LocalTime.parse(h[0]))&&!end.toLocalTime().isAfter(LocalTime.parse(h[1]));
        check(fits,"기존 승인·이용 중 신청 "+num(r,"REQUEST_ITEM_ID")+"과 충돌합니다. 해당 신청을 먼저 처리해주세요.");
      }
      db.update("UPDATE FACILITY SET FACILITY_CODE=?,FACILITY_NAME=?,FACILITY_TYPE=?,LOCATION_TEXT=?,CAPACITY=?,FIXED_EQUIPMENT_INFO=?,USE_ALLOWED_YN=?,OPERATING_STATUS=?,ROW_VERSION=ROW_VERSION+1 WHERE FACILITY_ID=?",code,name,type,place,capacity,info,allowed,status,id);
      db.update("DELETE FROM FACILITY_HOURS WHERE FACILITY_ID=?",id);db.update("DELETE FROM FACILITY_PURPOSE WHERE FACILITY_ID=?",id);
    }else{id=next("FACILITY");db.update("INSERT INTO FACILITY(FACILITY_ID,FACILITY_CODE,FACILITY_NAME,FACILITY_TYPE,LOCATION_TEXT,CAPACITY,FIXED_EQUIPMENT_INFO,USE_ALLOWED_YN,OPERATING_STATUS,IS_DEMO_YN) VALUES(?,?,?,?,?,?,?,?,?,'Y')",id,code,name,type,place,capacity,info,allowed,status);}
    for(Map.Entry<Integer,String[]> entry:hours.entrySet())db.update("INSERT INTO FACILITY_HOURS(FACILITY_ID,WEEKDAY_NO,OPEN_TIME,CLOSE_TIME) VALUES(?,?,?,?)",id,entry.getKey(),entry.getValue()[0],entry.getValue()[1]);
    for(Long purpose:purposes)db.update("INSERT INTO FACILITY_PURPOSE(FACILITY_ID,PURPOSE_ID) VALUES(?,?)",id,purpose);
    return id;
  }
  @Transactional
  public long saveBlock(long facilityId,Long blockId,JsonNode input,Map<String,Object> user){
    admin(user);Map<String,Object> facility=rental.lockResource("F"+facilityId);version(facility,input);
    Timestamp start=rental.time(input,"start"),end=rental.time(input,"end");check(end.after(start),"제한 종료는 시작보다 늦어야 합니다.");String reason=required(input,"reason",500);
    check(db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM WHERE FACILITY_ID=? AND STATUS_CODE IN ('APPROVED','IN_USE') AND USE_START_AT<? AND USE_END_AT>?",Integer.class,facilityId,end,start)==0,"기존 승인·이용 중 예약과 제한 일정이 겹칩니다. 해당 예약을 먼저 처리해주세요.");
    check(db.queryForObject("SELECT COUNT(*) FROM FACILITY_BLOCK WHERE ENABLED_YN='Y' AND FACILITY_ID=? AND BLOCK_ID<>? AND BLOCK_START_AT<? AND BLOCK_END_AT>?",Integer.class,facilityId,blockId==null?-1:blockId,end,start)==0,"다른 제한 일정과 겹칩니다.");
    String before=null,action=blockId==null?"CREATE":"UPDATE";
    if(blockId!=null){Map<String,Object> old=db.queryForMap("SELECT * FROM FACILITY_BLOCK WHERE FACILITY_ID=? AND BLOCK_ID=?",facilityId,blockId);check("Y".equals(old.get("ENABLED_YN")),"해제된 제한 일정은 수정할 수 없습니다.");before=blockJson(old);}
    if(blockId==null){blockId=next("FACILITY_BLOCK");db.update("INSERT INTO FACILITY_BLOCK(BLOCK_ID,FACILITY_ID,BLOCK_START_AT,BLOCK_END_AT,REASON_TEXT,REGISTERED_BY) VALUES(?,?,?,?,?,?)",blockId,facilityId,start,end,reason,userId(user));}
    else check(db.update("UPDATE FACILITY_BLOCK SET BLOCK_START_AT=?,BLOCK_END_AT=?,REASON_TEXT=?,REGISTERED_BY=? WHERE BLOCK_ID=? AND FACILITY_ID=?",start,end,reason,userId(user),blockId,facilityId)==1,"이용 제한을 찾을 수 없습니다.");
    audit(facilityId,blockId,action,before,reason,user);
    db.update("UPDATE FACILITY SET ROW_VERSION=ROW_VERSION+1 WHERE FACILITY_ID=?",facilityId);return blockId;
  }
  String blockJson(Map<String,Object> row){try{return json.writeValueAsString(obj("start",date(row.get("BLOCK_START_AT")),"end",date(row.get("BLOCK_END_AT")),"reason",str(row,"REASON_TEXT"),"enabled",str(row,"ENABLED_YN"),"cancelReason",str(row,"CANCEL_REASON")));}catch(Exception e){throw new IllegalStateException("이력을 저장할 수 없습니다.");}}
  void audit(long facility,long block,String action,String before,String reason,Map<String,Object> user){
    String after=blockJson(db.queryForMap("SELECT * FROM FACILITY_BLOCK WHERE BLOCK_ID=?",block));
    db.update("INSERT INTO TBL_FACILITY_BLOCK_LOG(AUDIT_ID,BLOCK_ID,FACILITY_ID,ACTION_CODE,BEFORE_JSON,AFTER_JSON,REASON_TEXT,ACTOR_ID) VALUES(SEQ_FACILITY_BLOCK_LOG.NEXTVAL,?,?,?,?,?,?,?)",block,facility,action,before,after,reason,userId(user));
  }
  @Transactional
  public void cancelBlock(long facility,long block,JsonNode input,Map<String,Object> user){
    admin(user);version(rental.lockResource("F"+facility),input);String reason=required(input,"reason",500);
    Map<String,Object> old=db.queryForMap("SELECT * FROM FACILITY_BLOCK WHERE FACILITY_ID=? AND BLOCK_ID=?",facility,block);check("Y".equals(old.get("ENABLED_YN")),"이미 해제된 제한입니다.");
    db.update("UPDATE FACILITY_BLOCK SET ENABLED_YN='N',CANCELLED_AT=LOCALTIMESTAMP,CANCELLED_BY=?,CANCEL_REASON=? WHERE BLOCK_ID=?",userId(user),reason,block);
    audit(facility,block,"CANCEL",blockJson(old),reason,user);db.update("UPDATE FACILITY SET ROW_VERSION=ROW_VERSION+1 WHERE FACILITY_ID=?",facility);
  }
  @Transactional(readOnly=true)
  public Object blockHistory(long facility,Map<String,Object> user){admin(user);return db.query("SELECT L.*,U.USER_NAME FROM TBL_FACILITY_BLOCK_LOG L JOIN APP_USER U ON U.USER_ID=L.ACTOR_ID WHERE FACILITY_ID=? ORDER BY AUDIT_ID",(rs,n)->obj("action",rs.getString("ACTION_CODE"),"at",date(rs.getTimestamp("CREATED_AT")),"by",rs.getString("USER_NAME"),"reason",rs.getString("REASON_TEXT"),"before",rs.getString("BEFORE_JSON"),"after",rs.getString("AFTER_JSON")),facility);}
}
