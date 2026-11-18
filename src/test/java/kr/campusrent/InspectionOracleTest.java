package kr.campusrent;
import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;
import static kr.campusrent.RentalService.obj;import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"debug=false","logging.level.root=WARN","logging.level.org.springframework=WARN","logging.level.com.zaxxer.hikari=WARN"})
@Transactional
@EnabledIfSystemProperty(named="oracle.integration",matches="true")
class InspectionOracleTest {
 @Autowired RentalService rental;@Autowired AdminService management;@Autowired JdbcTemplate db;
 ObjectMapper json=new ObjectMapper();Map<String,Object> admin=obj("id","1","role","admin"),student=obj("id","2","role","student"),other=obj("id","3","role","student");long asset;String item;
 JsonNode node(Object value){return json.valueToTree(value);}
 void change(String target,int version,Map<String,Object> who,Object details){rental.transition(Long.parseLong(item),node(obj("target",target,"version",version,"details",details)),who);}
 @BeforeEach void setup(){asset=management.saveEquipment(null,node(obj("code","INS-"+UUID.randomUUID().toString().substring(0,12),"name","검수 시험","modelId",2,"location","시험","allowed",true,"status","NORMAL","components",Arrays.asList(obj("name","본체","quantity",1,"body",true,"required",true),obj("name","배터리","quantity",2,"body",false,"required",true)))),admin);String day=LocalDate.now(RentalService.KOREA).plusDays(10).toString();item=rental.submit(node(Arrays.asList(obj("resourceId","E"+asset,"condition",obj("start",day+"T10:00","end",day+"T12:00")))),student).get(0);change("승인",1,admin,Collections.emptyMap());}
 void out(){change("출고",2,admin,obj("confirmed",true));change("반납 요청",3,student,Collections.emptyMap());}
 @Test void damageRoundAndAdditionalReturnKeepNotesAndTotals(){out();change("반납 보류",4,admin,obj("reason","배터리 추가 반환 대기","quantities",obj("본체",1,"배터리",1),"components",obj("본체",obj("condition","DAMAGED","note","렌즈 커버 파손"))));
  assertEquals("CHECKING",db.queryForObject("SELECT OPERATING_STATUS FROM EQUIPMENT WHERE EQUIPMENT_ID=?",String.class,asset));
  change("반납 완료",5,admin,obj("quantities",obj("본체",0,"배터리",1),"components",obj("배터리",obj("condition","NORMAL","note","추가 반환 확인"))));
  Map<String,Object> history=rental.inspectionHistory(Long.parseLong(item),student);List<?> rounds=(List<?>)history.get("rounds");assertEquals(2,rounds.size());
  assertTrue(rounds.toString().contains("렌즈 커버 파손"));assertTrue(rounds.toString().contains("추가 반환 확인"));assertTrue(rounds.toString().contains("CUMULATIVE_QTY=2"));
  assertEquals("CHECKING",db.queryForObject("SELECT OPERATING_STATUS FROM EQUIPMENT WHERE EQUIPMENT_ID=?",String.class,asset));
  assertThrows(IllegalArgumentException.class,()->rental.inspectionHistory(Long.parseLong(item),other));assertThrows(IllegalArgumentException.class,()->rental.inspectionHistory(Long.parseLong(item),null));
 }
 @Test void damagedReturnRequiresNote(){out();assertThrows(IllegalArgumentException.class,()->change("반납 완료",4,admin,obj("quantities",obj("본체",1,"배터리",2),"components",obj("본체",obj("condition","DAMAGED")))));}
 @Test void missingConditionCannotHaveReturnedQuantity(){out();assertThrows(IllegalArgumentException.class,()->change("반납 완료",4,admin,obj("quantities",obj("본체",1,"배터리",2),"components",obj("배터리",obj("condition","MISSING")))));}
 Map<String,Object> backfill(){return obj("confirmed",true,"source","BACKFILL","reason","현장 전달 후 등록","actualAt",LocalDateTime.now(RentalService.KOREA).minusMinutes(5).withNano(0).toString(),"parts",Arrays.asList(obj("name","본체","quantity",1,"condition","NORMAL"),obj("name","배터리","quantity",1,"condition","NORMAL","note","실제 1개 전달")));}
 @Test void backfillKeepsActualTimeSourceAndDeliveredSnapshot(){Map<String,Object> draft=backfill();change("출고",2,admin,draft);Map<String,Object> h=rental.inspectionHistory(Long.parseLong(item),admin);Map<?,?> c=(Map<?,?>)h.get("checkout");assertEquals("BACKFILL",c.get("SOURCE_CODE"));assertNotNull(c.get("ACTUAL_CHECKOUT_AT"));assertTrue(h.toString().contains("현장 전달 후 등록"));assertTrue(h.toString().contains("실제 1개 전달"));assertThrows(IllegalArgumentException.class,()->change("출고",2,admin,draft));change("반납 요청",3,student,Collections.emptyMap());change("반납 완료",4,admin,obj("quantities",obj("본체",1,"배터리",1)));assertEquals("RETURNED",db.queryForObject("SELECT STATUS_CODE FROM REQUEST_ITEM WHERE REQUEST_ITEM_ID=?",String.class,Long.valueOf(item)));}
 @Test void backfillRejectsFutureAndStudent(){Map<String,Object> draft=backfill();assertThrows(IllegalArgumentException.class,()->change("출고",2,student,draft));draft.put("actualAt",LocalDateTime.now(RentalService.KOREA).plusDays(1).toString());assertThrows(IllegalArgumentException.class,()->change("출고",2,admin,draft));}
 @Test void backfillRequiresReason(){Map<String,Object> draft=backfill();draft.put("reason","");assertThrows(IllegalArgumentException.class,()->change("출고",2,admin,draft));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM EQUIPMENT_CHECKOUT WHERE REQUEST_ITEM_ID=?",Integer.class,Long.valueOf(item)));}
 @Test void backfillCannotOverlapCompletedPhysicalCheckout(){out();change("반납 완료",4,admin,obj("quantities",obj("본체",1,"배터리",2)));String day=LocalDate.now(RentalService.KOREA).plusDays(12).toString();item=rental.submit(node(Arrays.asList(obj("resourceId","E"+asset,"condition",obj("start",day+"T10:00","end",day+"T12:00")))),student).get(0);change("승인",1,admin,Collections.emptyMap());assertThrows(IllegalArgumentException.class,()->change("출고",2,admin,backfill()));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM EQUIPMENT_CHECKOUT WHERE REQUEST_ITEM_ID=?",Integer.class,Long.valueOf(item)));}
}
