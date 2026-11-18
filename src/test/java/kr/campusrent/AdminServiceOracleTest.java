package kr.campusrent;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"debug=false","logging.level.root=WARN","logging.level.org.springframework=WARN","logging.level.com.zaxxer.hikari=WARN"})
@Transactional
@EnabledIfSystemProperty(named="oracle.integration",matches="true")
class AdminServiceOracleTest {
  @Autowired AdminService management;
  @Autowired ModelService models;
  @Autowired RentalService rental;
  @Autowired JdbcTemplate db;
  final ObjectMapper json=new ObjectMapper();
  final Map<String,Object> admin=RentalService.obj("id","1","role","admin","name","관리자");
  final Map<String,Object> student=RentalService.obj("id","2","role","student","name","학생");
  JsonNode node(Object value){return json.valueToTree(value);}
  Map<String,Object> equipment(){return RentalService.obj("code","TEST-"+UUID.randomUUID().toString().substring(0,12),"name","관리 시험 장비","modelId",2,"location","검증 보관실","department","검증","allowed",true,"status","NORMAL","version",1,"components",Arrays.asList(RentalService.obj("name","본체","quantity",1,"body",true,"required",true)));}
  Map<String,Object> facility(){List<Object> hours=new ArrayList<>();for(int i=1;i<=7;i++)hours.add(RentalService.obj("day",i,"open","09:00","close","20:00"));return RentalService.obj("code","TEST-"+UUID.randomUUID().toString().substring(0,12),"name","관리 시험 시설","type","회의실","location","검증 장소","info","테이블","capacity",8,"allowed",true,"status","NORMAL","version",1,"purposeIds",Arrays.asList(3),"hours",hours);}
  String request(String resource){String day=LocalDate.now(RentalService.KOREA).plusDays(25).toString();return rental.submit(node(Arrays.asList(RentalService.obj("resourceId",resource,"condition",RentalService.obj("start",day+"T10:00","end",day+"T12:00","people",6,"use",resource.startsWith("F")?"팀 회의":"")))),student).get(0);}
  void transition(String id,String target,int version,Object details){rental.transition(Long.parseLong(id),node(RentalService.obj("target",target,"version",version,"details",details)),admin);}
  @Test void equipmentEditsPreserveCheckoutSnapshot(){
    Map<String,Object> draft=equipment();long id=management.saveEquipment(null,node(draft),admin);String item=request("E"+id);transition(item,"승인",1,Collections.emptyMap());transition(item,"출고",2,RentalService.obj("confirmed",true));
    draft.put("name","이름 변경");draft.put("components",Arrays.asList(RentalService.obj("name","새 본체","quantity",2,"body",true,"required",true)));management.saveEquipment(id,node(draft),admin);
    assertEquals("본체",db.queryForObject("SELECT C.COMPONENT_NAME FROM CHECKOUT_COMPONENT C JOIN EQUIPMENT_CHECKOUT E ON E.CHECKOUT_ID=C.CHECKOUT_ID WHERE E.REQUEST_ITEM_ID=?",String.class,Long.valueOf(item)));
    assertEquals(1,db.queryForObject("SELECT C.DELIVERED_QTY FROM CHECKOUT_COMPONENT C JOIN EQUIPMENT_CHECKOUT E ON E.CHECKOUT_ID=C.CHECKOUT_ID WHERE E.REQUEST_ITEM_ID=?",Integer.class,Long.valueOf(item)));
    assertEquals("새 본체",db.queryForObject("SELECT COMPONENT_NAME FROM EQUIPMENT_COMPONENT WHERE EQUIPMENT_ID=?",String.class,id));
    assertThrows(IllegalArgumentException.class,()->management.saveEquipment(id,node(draft),admin));
  }
  @Test void approvedEquipmentCannotBeStopped(){
    Map<String,Object> draft=equipment();long id=management.saveEquipment(null,node(draft),admin);String item=request("E"+id);transition(item,"승인",1,Collections.emptyMap());
    draft.put("status","STOPPED");draft.put("reason","정지 시험");assertThrows(IllegalArgumentException.class,()->management.saveEquipment(id,node(draft),admin));
    assertEquals("NORMAL",db.queryForObject("SELECT OPERATING_STATUS FROM EQUIPMENT WHERE EQUIPMENT_ID=?",String.class,id));
  }
  @Test void directedAlternativeCanBeDisabled(){
    long source=management.saveEquipment(null,node(equipment()),admin),target=management.saveEquipment(null,node(equipment()),admin);
    Map<String,Object> draft=RentalService.obj("sourceId",source,"targetId",target,"reason","동일 품목 후보","difference","차이 확인","enabled",true,"existing",false);
    management.saveAlternative(node(draft),admin);assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM EQUIPMENT_ALTERNATIVE WHERE SOURCE_EQUIPMENT_ID=? AND TARGET_EQUIPMENT_ID=?",Integer.class,target,source));
    draft.put("existing",true);draft.put("expected",RentalService.obj("reason","동일 품목 후보","difference","차이 확인","enabled",true));draft.put("enabled",false);management.saveAlternative(node(draft),admin);
    assertEquals("N",db.queryForObject("SELECT ENABLED_YN FROM EQUIPMENT_ALTERNATIVE WHERE SOURCE_EQUIPMENT_ID=? AND TARGET_EQUIPMENT_ID=?",String.class,source,target));
    assertThrows(IllegalArgumentException.class,()->management.saveAlternative(node(draft),admin));
  }
  @Test void invalidAlternativeIsRejected(){
    long source=management.saveEquipment(null,node(equipment()),admin);
    assertThrows(IllegalArgumentException.class,()->management.saveAlternative(node(RentalService.obj("sourceId",source,"targetId",source,"reason","자기 자신","enabled",true)),admin));
    assertThrows(IllegalArgumentException.class,()->management.saveAlternative(node(RentalService.obj("sourceId",source,"targetId",3,"reason","다른 품목","enabled",true)),admin));
  }
  @Test void facilityChangesProtectApprovedReservations(){
    Map<String,Object> draft=facility();long id=management.saveFacility(null,node(draft),admin);String item=request("F"+id);transition(item,"승인",1,Collections.emptyMap());
    draft.put("capacity",4);assertThrows(IllegalArgumentException.class,()->management.saveFacility(id,node(draft),admin));
    assertEquals(8,db.queryForObject("SELECT CAPACITY FROM FACILITY WHERE FACILITY_ID=?",Integer.class,id));
    String day=LocalDate.now(RentalService.KOREA).plusDays(25).toString();
    assertThrows(IllegalArgumentException.class,()->management.saveBlock(id,null,node(RentalService.obj("version",1,"start",day+"T11:00","end",day+"T13:00","reason","예약과 충돌")),admin));
  }
  @Test void facilityHoursAndBlocksAreSavedWithVersion(){
    Map<String,Object> draft=facility();draft.put("hours",Arrays.asList(RentalService.obj("day",1,"open","10:00","close","18:00")));
    long id=management.saveFacility(null,node(draft),admin);assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM FACILITY_HOURS WHERE FACILITY_ID=?",Integer.class,id));
    Map<String,Object> published=rental.catalog().stream().filter(r->r.get("id").equals("F"+id)).findFirst().get();assertEquals(1,((Map<?,?>)published.get("hoursByDay")).size());
    String day=LocalDate.now(RentalService.KOREA).plusDays(28).toString();long block=management.saveBlock(id,null,node(RentalService.obj("version",1,"start",day+"T10:00","end",day+"T12:00","reason","운영 점검")),admin);
    assertEquals(2,db.queryForObject("SELECT ROW_VERSION FROM FACILITY WHERE FACILITY_ID=?",Integer.class,id));
    management.saveBlock(id,block,node(RentalService.obj("version",2,"start",day+"T10:00","end",day+"T13:00","reason","시간 변경")),admin);
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM FACILITY_BLOCK WHERE FACILITY_ID=?",Integer.class,id));
    assertThrows(IllegalArgumentException.class,()->management.saveFacility(id,node(draft),admin));
  }
  @Test void managementRequiresAdmin(){
    assertThrows(IllegalArgumentException.class,()->management.snapshot(student));assertThrows(IllegalArgumentException.class,()->management.saveEquipment(null,node(equipment()),student));assertThrows(IllegalArgumentException.class,()->management.saveFacility(null,node(facility()),student));
  }
  @Test void cancelledBlocksKeepAuditAndReleaseAvailability(){
    long f=management.saveFacility(null,node(facility()),admin);String day=LocalDate.now(RentalService.KOREA).plusDays(25).toString();
    long b=management.saveBlock(f,null,node(RentalService.obj("version",1,"start",day+"T10:00","end",day+"T12:00","reason","점검")),admin);
    assertThrows(IllegalArgumentException.class,()->request("F"+f));
    management.cancelBlock(f,b,node(RentalService.obj("version",2,"reason","점검 완료")),admin);
    assertEquals("N",db.queryForObject("SELECT ENABLED_YN FROM FACILITY_BLOCK WHERE BLOCK_ID=?",String.class,b));
    assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM TBL_FACILITY_BLOCK_LOG WHERE BLOCK_ID=?",Integer.class,b));
    assertNotNull(request("F"+f));
    assertThrows(IllegalArgumentException.class,()->management.cancelBlock(f,b,node(RentalService.obj("version",3,"reason","중복")),admin));
    List<?> reservations=(List<?>)rental.state(null).get("reservations");assertFalse(reservations.toString().contains("block-"+b));
  }
  @Test void modelsAndSpecsValidateRevisionAndType(){
    String suffix=UUID.randomUUID().toString().substring(0,8);long type=models.saveType(null,node(RentalService.obj("name","시험 품목 "+suffix,"category","시험","enabled",true)),admin);
    long spec=models.saveItem(null,node(RentalService.obj("typeId",type,"name","해상도","unit","px","order",1)),admin);
    Map<String,Object> draft=RentalService.obj("typeId",type,"code","TEST-M-"+suffix,"name","시험 모델","manufacturer","시험","demo",true,"specs",Arrays.asList(RentalService.obj("itemId",spec,"value","1920","unit","px","status","CONFIRMED")));
    long model=models.saveModel(null,node(draft),admin);Map<String,Object> row=db.queryForMap("SELECT * FROM EQUIPMENT_MODEL WHERE MODEL_ID=?",model);draft.put("revision",models.revision(row,models.specs(model)));draft.put("name","수정 모델");models.saveModel(model,node(draft),admin);
    assertEquals("수정 모델",db.queryForObject("SELECT MODEL_NAME FROM EQUIPMENT_MODEL WHERE MODEL_ID=?",String.class,model));
    assertThrows(IllegalArgumentException.class,()->models.saveModel(model,node(draft),admin));
    draft.put("specs",Arrays.asList(RentalService.obj("itemId",1,"value","1","status","CONFIRMED")));assertThrows(IllegalArgumentException.class,()->models.saveModel(null,node(draft),admin));
    assertThrows(IllegalArgumentException.class,()->models.snapshot(student));
    Map<String,Object> asset=equipment();asset.put("modelId",model);long equipment=management.saveEquipment(null,node(asset),admin);
    Map<String,Object> oldType=db.queryForMap("SELECT * FROM EQUIPMENT_TYPE WHERE TYPE_ID=?",type);
    models.saveType(type,node(RentalService.obj("name","시험 품목 "+suffix,"category","시험","enabled",false,"revision",models.revision(oldType,Collections.emptyList()))),admin);
    Map<String,Object> published=rental.catalog().stream().filter(r->r.get("id").equals("E"+equipment)).findFirst().get();assertEquals(false,published.get("rentable"));
  }
  @Test void originalSelectionAndPurposeArePersisted(){
    long asset=management.saveEquipment(null,node(equipment()),admin);String day=LocalDate.now(RentalService.KOREA).plusDays(26).toString();
    Map<String,Object> original=RentalService.obj("resourceId","E"+asset,"condition",RentalService.obj("start",day+"T10:00","end",day+"T12:00"));
    Map<String,Object> item=RentalService.obj("resourceId","E"+asset,"condition",RentalService.obj("start",day+"T13:00","end",day+"T15:00"),"originCode","ALTERNATE","original",original);
    long id=Long.parseLong(rental.submit(node(Arrays.asList(item)),student).get(0));
    assertEquals("OTHER_TIME",db.queryForObject("SELECT ALTERNATIVE_KIND FROM REQUEST_ALTERNATIVE WHERE REQUEST_ITEM_ID=?",String.class,id));
    assertEquals(asset,db.queryForObject("SELECT ORIGINAL_EQUIPMENT_ID FROM REQUEST_ALTERNATIVE WHERE REQUEST_ITEM_ID=?",Long.class,id));
    item.put("originCode","PURPOSE");item.put("activity","영상 촬영");id=Long.parseLong(rental.submit(node(Arrays.asList(item)),student).get(0));
    assertEquals("영상 촬영",db.queryForObject("SELECT ACTIVITY_TEXT FROM REQUEST_ITEM WHERE REQUEST_ITEM_ID=?",String.class,id));
  }
}
