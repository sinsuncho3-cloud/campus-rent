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

/** Explicit opt-in Oracle integration tests. Test rows roll back; sequence numbers may advance. */
@SpringBootTest(properties={"debug=false","logging.level.root=WARN","logging.level.org.springframework=WARN","logging.level.com.zaxxer.hikari=WARN"})
@Transactional
@EnabledIfSystemProperty(named="oracle.integration",matches="true")
class RentalServiceOracleTest {
  @Autowired RentalService service;
  @Autowired JdbcTemplate db;
  final ObjectMapper json=new ObjectMapper();
  Map<String,Object> student=RentalService.obj("id","2","role","student","name","시연 사용자");
  Map<String,Object> other=RentalService.obj("id","3","role","student","name","다른 사용자");
  Map<String,Object> admin=RentalService.obj("id","1","role","admin","name","시연 관리자");
  JsonNode node(Object value){return json.valueToTree(value);}
  Map<String,Object> condition(String purpose,int people){String day=LocalDate.now(RentalService.KOREA).plusDays(20).toString();return RentalService.obj("start",day+"T10:00","end",day+"T12:00","people",people,"use",purpose);}
  String submit(String resource,Map<String,Object> c){return service.submit(node(Arrays.asList(RentalService.obj("resourceId",resource,"condition",c))),student).get(0);}
  void change(String id,String target,int version,Map<String,Object> who,Object details){service.transition(Long.parseLong(id),node(RentalService.obj("target",target,"version",version,"details",details)),who);}
  String status(String id){return db.queryForObject("SELECT STATUS_CODE FROM REQUEST_ITEM WHERE REQUEST_ITEM_ID=?",String.class,Long.valueOf(id));}
  @Test void signupAndPasswordAreStoredInOracle(){
    String email="integration-"+UUID.randomUUID()+"@campusrent.example";
    JsonNode signup=node(RentalService.obj("name","통합검증","email",email,"password","Integration123!","role","admin"));
    Map<String,Object> user=service.signup(signup);assertEquals("student",user.get("role"));
    String hash=db.queryForObject("SELECT PASSWORD FROM APP_USER WHERE EMAIL=?",String.class,email);
    assertNotEquals("Integration123!",hash);assertTrue(hash.startsWith("{pbkdf2@SpringSecurity_v5_8}"));
    assertEquals(user.get("id"),service.login(signup).get("id"));
    assertThrows(IllegalArgumentException.class,()->service.login(node(RentalService.obj("email",email,"password","wrong"))));
  }
  @Test void requestOwnershipAndReservationConflict(){
    String first=submit("E2",condition("",1)),second=submit("E2",condition("",1));
    assertEquals("PENDING",status(first));
    assertThrows(IllegalArgumentException.class,()->change(first,"취소",1,other,Collections.emptyMap()));
    assertThrows(IllegalArgumentException.class,()->change(first,"승인",1,student,Collections.emptyMap()));
    change(first,"승인",1,admin,Collections.emptyMap());assertEquals("APPROVED",status(first));
    assertThrows(IllegalArgumentException.class,()->change(second,"승인",1,admin,Collections.emptyMap()));
    assertThrows(IllegalArgumentException.class,()->change(first,"출고",1,admin,RentalService.obj("confirmed",true)));
  }
  @Test void checkoutHoldAdditionalReturnAndInspection(){
    String id=submit("E2",condition("",1));change(id,"승인",1,admin,Collections.emptyMap());
    change(id,"출고",2,admin,RentalService.obj("confirmed",true));change(id,"반납 요청",3,student,Collections.emptyMap());
    Map<String,Object> partial=RentalService.obj("카메라 본체",1,"기본 렌즈",1,"배터리",0,"충전기",0);
    change(id,"반납 보류",4,admin,RentalService.obj("reason","배터리와 충전기 추가 반환 대기","quantities",partial));
    assertEquals("RETURN_HOLD",status(id));
    Map<String,Object> rest=RentalService.obj("카메라 본체",0,"기본 렌즈",0,"배터리",1,"충전기",1);
    change(id,"반납 완료",5,admin,RentalService.obj("quantities",rest,"needsInspection",true));
    assertEquals("RETURNED",status(id));
    assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM RETURN_INSPECTION R JOIN EQUIPMENT_CHECKOUT C ON C.CHECKOUT_ID=R.CHECKOUT_ID WHERE C.REQUEST_ITEM_ID=?",Integer.class,Long.valueOf(id)));
    assertEquals("CHECKING",db.queryForObject("SELECT OPERATING_STATUS FROM EQUIPMENT WHERE EQUIPMENT_ID=2",String.class));
    service.finishInspection("E2",admin);assertEquals("NORMAL",db.queryForObject("SELECT OPERATING_STATUS FROM EQUIPMENT WHERE EQUIPMENT_ID=2",String.class));
  }
  @Test void rejectsOverReturn(){
    String id=submit("E2",condition("",1));change(id,"승인",1,admin,Collections.emptyMap());change(id,"출고",2,admin,RentalService.obj("confirmed",true));change(id,"반납 요청",3,student,Collections.emptyMap());
    assertThrows(IllegalArgumentException.class,()->change(id,"반납 완료",4,admin,RentalService.obj("quantities",RentalService.obj("카메라 본체",2,"기본 렌즈",1,"배터리",1,"충전기",1))));
    assertEquals("RETURN_REQUESTED",status(id));
  }
  @Test void facilityLifecycle(){
    String id=submit("F2",condition("팀 회의",6));change(id,"승인",1,admin,Collections.emptyMap());change(id,"이용 중",2,admin,Collections.emptyMap());change(id,"이용 완료",3,admin,Collections.emptyMap());
    assertEquals("COMPLETED",status(id));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM FACILITY_USAGE WHERE REQUEST_ITEM_ID=? AND ENDED_AT IS NOT NULL",Integer.class,Long.valueOf(id)));
  }
  @Test void facilityCapacityIsCheckedByServer(){assertThrows(IllegalArgumentException.class,()->submit("F1",condition("팀 회의",6)));}
  @Test void publicStateDoesNotContainApplicantData(){
    String id=submit("E2",condition("",1));change(id,"승인",1,admin,Collections.emptyMap());
    Map<String,Object> state=service.state(null);assertTrue(((List<?>)state.get("records")).isEmpty());
    List<?> reservations=(List<?>)state.get("reservations");assertFalse(reservations.isEmpty());for(Object r:reservations){assertFalse(((Map<?,?>)r).containsKey("owner"));assertFalse(((Map<?,?>)r).containsKey("ownerName"));}
  }
}
