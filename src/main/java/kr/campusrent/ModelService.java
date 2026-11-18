package kr.campusrent;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static kr.campusrent.RentalService.*;

@Service
public class ModelService {
  final JdbcTemplate db;final RentalService rental;
  public ModelService(JdbcTemplate db,RentalService rental){this.db=db;this.rental=rental;}
  String required(JsonNode n,String key,int max){String s=text(n,key,max);check(!s.isEmpty(),"필수 항목을 입력해주세요.");return s;}
  long next(String table){check(Arrays.asList("EQUIPMENT_TYPE","EQUIPMENT_MODEL","SPEC_ITEM").contains(table),"잘못된 식별자입니다.");return db.queryForObject("SELECT SQ_"+table+".NEXTVAL FROM DUAL",Long.class);}
  String revision(Map<String,Object> row,List<Map<String,Object>> specs){
    try{TreeMap<String,Object> sorted=new TreeMap<>(row);sorted.remove("REVISION");String input=sorted.toString()+specs.toString();byte[] bytes=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(String.format("%02x",b&255));return hex.toString();}catch(Exception e){throw new IllegalStateException("변경 기준을 계산하지 못했습니다.");}
  }
  void expected(JsonNode input,String revision){check(revision.equals(text(input,"revision",64)),"다른 창에서 수정되었습니다. 새로 조회해주세요.");}
  List<Map<String,Object>> specs(long id){return db.queryForList("SELECT * FROM MODEL_SPEC WHERE MODEL_ID=? ORDER BY SPEC_ITEM_ID",id);}
  void lockAssets(String column,long id){
    check(Arrays.asList("M.TYPE_ID","E.MODEL_ID").contains(column),"잘못된 잠금 대상입니다.");
    List<Long> ids=db.queryForList("SELECT E.EQUIPMENT_ID FROM EQUIPMENT E JOIN EQUIPMENT_MODEL M ON M.MODEL_ID=E.MODEL_ID WHERE "+column+"=?",Long.class,id);
    TreeSet<String> resources=new TreeSet<>();for(Long asset:ids)resources.add("E"+asset);for(String resource:resources)rental.lockResource(resource);
  }
  boolean active(String column,long id){return db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM I JOIN EQUIPMENT E ON E.EQUIPMENT_ID=I.EQUIPMENT_ID JOIN EQUIPMENT_MODEL M ON M.MODEL_ID=E.MODEL_ID WHERE "+column+"=? AND ((I.STATUS_CODE='APPROVED' AND I.USE_END_AT>LOCALTIMESTAMP) OR I.STATUS_CODE IN ('CHECKED_OUT','RETURN_REQUESTED','RETURN_HOLD'))",Integer.class,id)>0;}
  @Transactional(readOnly=true)
  public Map<String,Object> snapshot(Map<String,Object> user){
    admin(user);List<Map<String,Object>> types=db.queryForList("SELECT * FROM EQUIPMENT_TYPE ORDER BY TYPE_ID"),models=db.queryForList("SELECT * FROM EQUIPMENT_MODEL ORDER BY MODEL_ID"),items=db.queryForList("SELECT * FROM SPEC_ITEM ORDER BY TYPE_ID,DISPLAY_ORDER,SPEC_ITEM_ID"),values=db.queryForList("SELECT * FROM MODEL_SPEC ORDER BY MODEL_ID,SPEC_ITEM_ID");
    for(Map<String,Object> row:types)row.put("REVISION",revision(row,Collections.emptyList()));
    for(Map<String,Object> row:items)row.put("REVISION",revision(row,Collections.emptyList()));
    for(Map<String,Object> row:models){List<Map<String,Object>> own=new ArrayList<>();for(Map<String,Object> value:values)if(num(value,"MODEL_ID")==num(row,"MODEL_ID"))own.add(value);row.put("REVISION",revision(row,own));}
    return obj("types",types,"models",models,"items",items,"values",values);
  }
  @Transactional
  public long saveType(Long id,JsonNode input,Map<String,Object> user){
    admin(user);String name=required(input,"name",50),label=required(input,"category",50);check(input.path("enabled").isBoolean(),"사용 여부를 확인해주세요.");String enabled=input.path("enabled").asBoolean()?"Y":"N";
    if(id==null){id=next("EQUIPMENT_TYPE");db.update("INSERT INTO EQUIPMENT_TYPE(TYPE_ID,TYPE_NAME,CATEGORY_LABEL,ENABLED_YN) VALUES(?,?,?,?)",id,name,label,enabled);}
    else{lockAssets("M.TYPE_ID",id);Map<String,Object> old=db.queryForMap("SELECT * FROM EQUIPMENT_TYPE WHERE TYPE_ID=? FOR UPDATE WAIT 5",id);expected(input,revision(old,Collections.emptyList()));check(!"N".equals(enabled)||!active("M.TYPE_ID",id),"승인·미반납 신청이 있어 품목을 사용 중지할 수 없습니다.");db.update("UPDATE EQUIPMENT_TYPE SET TYPE_NAME=?,CATEGORY_LABEL=?,ENABLED_YN=? WHERE TYPE_ID=?",name,label,enabled,id);}
    return id;
  }
  @Transactional
  public long saveItem(Long id,JsonNode input,Map<String,Object> user){
    admin(user);long type=integer(input,"typeId",1,9999999999L);String name=required(input,"name",100),unit=text(input,"unit",30);long order=integer(input,"order",1,999);
    db.queryForMap("SELECT * FROM EQUIPMENT_TYPE WHERE TYPE_ID=? FOR UPDATE WAIT 5",type);
    if(id==null){id=next("SPEC_ITEM");db.update("INSERT INTO SPEC_ITEM(SPEC_ITEM_ID,TYPE_ID,SPEC_NAME,DEFAULT_UNIT,DISPLAY_ORDER) VALUES(?,?,?,?,?)",id,type,name,unit,order);}
    else{Map<String,Object> old=db.queryForMap("SELECT * FROM SPEC_ITEM WHERE SPEC_ITEM_ID=? FOR UPDATE WAIT 5",id);expected(input,revision(old,Collections.emptyList()));check(num(old,"TYPE_ID")==type,"이미 등록된 사양 항목의 품목은 변경할 수 없습니다.");db.update("UPDATE SPEC_ITEM SET SPEC_NAME=?,DEFAULT_UNIT=?,DISPLAY_ORDER=? WHERE SPEC_ITEM_ID=?",name,unit,order,id);}
    return id;
  }
  @Transactional
  public long saveModel(Long id,JsonNode input,Map<String,Object> user){
    admin(user);long type=integer(input,"typeId",1,9999999999L);String code=required(input,"code",30),name=required(input,"name",100),maker=text(input,"manufacturer",100);check(input.path("demo").isBoolean(),"가상 여부를 확인해주세요.");
    if(id!=null)lockAssets("E.MODEL_ID",id);
    Map<String,Object> kind=db.queryForMap("SELECT * FROM EQUIPMENT_TYPE WHERE TYPE_ID=? FOR UPDATE WAIT 5",type);check("Y".equals(kind.get("ENABLED_YN")),"사용 중인 품목을 선택해주세요.");
    if(id!=null){Map<String,Object> old=db.queryForMap("SELECT * FROM EQUIPMENT_MODEL WHERE MODEL_ID=? FOR UPDATE WAIT 5",id);expected(input,revision(old,specs(id)));check(num(old,"TYPE_ID")==type,"기존 모델의 품목은 변경할 수 없습니다. 다른 품목에는 새 모델을 등록해주세요.");check(!active("E.MODEL_ID",id),"승인·미반납 장비의 모델·사양은 변경할 수 없습니다.");}
    JsonNode values=input.path("specs");check(values.isArray()&&values.size()<=99,"모델 사양을 확인해주세요.");Set<Long> seen=new HashSet<>();
    for(JsonNode value:values){long item=integer(value,"itemId",1,9999999999L);check(seen.add(item)&&db.queryForObject("SELECT COUNT(*) FROM SPEC_ITEM WHERE SPEC_ITEM_ID=? AND TYPE_ID=?",Integer.class,item,type)==1,"같은 품목의 사양 항목을 선택해주세요.");String status=text(value,"status",10),v=text(value,"value",300);text(value,"unit",30);check(Arrays.asList("PENDING","CONFIRMED").contains(status)&&(!"CONFIRMED".equals(status)||!v.isEmpty()),"확인된 사양에는 값이 필요합니다.");}
    if(id==null){id=next("EQUIPMENT_MODEL");db.update("INSERT INTO EQUIPMENT_MODEL(MODEL_ID,TYPE_ID,MODEL_CODE,MODEL_NAME,MANUFACTURER,IS_DEMO_YN) VALUES(?,?,?,?,?,?)",id,type,code,name,maker,input.path("demo").asBoolean()?"Y":"N");}
    else{db.update("UPDATE EQUIPMENT_MODEL SET MODEL_CODE=?,MODEL_NAME=?,MANUFACTURER=?,IS_DEMO_YN=? WHERE MODEL_ID=?",code,name,maker,input.path("demo").asBoolean()?"Y":"N",id);db.update("DELETE FROM MODEL_SPEC WHERE MODEL_ID=?",id);}
    for(JsonNode value:values)db.update("INSERT INTO MODEL_SPEC(MODEL_ID,SPEC_ITEM_ID,SPEC_VALUE,VALUE_UNIT,VERIFY_STATUS) VALUES(?,?,?,?,?)",id,value.path("itemId").asLong(),text(value,"value",300),text(value,"unit",30),text(value,"status",10));
    return id;
  }
}
