package kr.campusrent;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Live committed concurrency checks. Own requests are completed/cancelled, never deleted. */
@SpringBootTest(properties={"debug=false","logging.level.root=WARN","logging.level.org.springframework=WARN","logging.level.com.zaxxer.hikari=WARN"})
@EnabledIfSystemProperty(named="oracle.stability",matches="true")
class OracleStabilityTest {
  @Autowired RentalService service;
  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager transactions;
  final ObjectMapper json=new ObjectMapper();
  final Map<String,Object> student=RentalService.obj("id","2","role","student","name","시연 사용자");
  final Map<String,Object> admin=RentalService.obj("id","1","role","admin","name","시연 관리자");
  Map<String,Object> item(String resource,int people){String day=LocalDate.now(RentalService.KOREA).plusDays(40).toString();return RentalService.obj("resourceId",resource,"condition",RentalService.obj("start",day+"T10:00","end",day+"T12:00","people",people,"use",resource.startsWith("F")?"팀 회의":""));}
  void change(String id,String label,long version,Map<String,Object> who,Object details){service.transition(Long.parseLong(id),json.valueToTree(RentalService.obj("target",label,"version",version,"details",details)),who);}
  void finish(String id){
    Map<String,Object> r=db.queryForMap("SELECT STATUS_CODE,ROW_VERSION FROM REQUEST_ITEM WHERE REQUEST_ITEM_ID=?",Long.valueOf(id));long v=RentalService.num(r,"ROW_VERSION");String s=RentalService.str(r,"STATUS_CODE");
    if("PENDING".equals(s)){change(id,"취소",v,student,Collections.emptyMap());return;}
    if("APPROVED".equals(s)){change(id,"출고",v++,admin,RentalService.obj("confirmed",true));s="CHECKED_OUT";}
    if("CHECKED_OUT".equals(s)){change(id,"반납 요청",v++,student,Collections.emptyMap());s="RETURN_REQUESTED";}
    if("RETURN_REQUESTED".equals(s))change(id,"반납 완료",v,admin,RentalService.obj("quantities",RentalService.obj("카메라 본체",1,"기본 렌즈",1,"배터리",1,"충전기",1)));
  }
  @Test void twoIndependentTransactionsCannotApproveSameResource()throws Exception{
    String a=service.submit(json.valueToTree(Arrays.asList(item("E1",1))),student).get(0);
    String b=service.submit(json.valueToTree(Arrays.asList(item("E1",1))),student).get(0);
    ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
    try{
      List<Future<Boolean>> results=new ArrayList<>();
      for(String id:Arrays.asList(a,b))results.add(pool.submit(()->{ready.countDown();start.await();try{change(id,"승인",1,admin,Collections.emptyMap());return true;}catch(IllegalArgumentException e){return false;}}));
      assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();int successes=0;for(Future<Boolean> f:results)if(f.get(20,TimeUnit.SECONDS))successes++;
      assertEquals(1,successes,"Only one independently committed approval may succeed");
      assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM WHERE REQUEST_ITEM_ID IN (?,?) AND STATUS_CODE='APPROVED'",Integer.class,Long.valueOf(a),Long.valueOf(b)));
    }finally{start.countDown();pool.shutdownNow();finish(a);finish(b);}
  }
  @Test void failureAfterFirstInsertRollsBackEntireRequest(){
    int requests=db.queryForObject("SELECT COUNT(*) FROM RENT_REQUEST",Integer.class),items=db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM",Integer.class),logs=db.queryForObject("SELECT COUNT(*) FROM REQUEST_STATUS_LOG",Integer.class);
    assertThrows(IllegalArgumentException.class,()->service.submit(json.valueToTree(Arrays.asList(item("E1",1),item("F1",999))),student));
    assertEquals(requests,db.queryForObject("SELECT COUNT(*) FROM RENT_REQUEST",Integer.class));assertEquals(items,db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM",Integer.class));assertEquals(logs,db.queryForObject("SELECT COUNT(*) FROM REQUEST_STATUS_LOG",Integer.class));
  }
  @Test void concurrentRetryCommitsOneRequest()throws Exception{
    String key=UUID.randomUUID().toString();JsonNode items=json.valueToTree(Arrays.asList(item("E1",1)));ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);Set<String> ids=new HashSet<>();
    try{List<Future<List<String>>> futures=new ArrayList<>();for(int i=0;i<2;i++)futures.add(pool.submit(()->{start.await();return service.submitOnce(items,key,student);}));start.countDown();for(Future<List<String>> f:futures)ids.addAll(f.get(25,TimeUnit.SECONDS));assertEquals(1,ids.size());}
    finally{start.countDown();pool.shutdown();pool.awaitTermination(30,TimeUnit.SECONDS);for(String id:ids)finish(id);}
  }
  @Test void databaseConstraintFailureRollsBackEarlierWrites(){
    int requests=db.queryForObject("SELECT COUNT(*) FROM RENT_REQUEST",Integer.class),items=db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM",Integer.class),logs=db.queryForObject("SELECT COUNT(*) FROM REQUEST_STATUS_LOG",Integer.class);
    assertThrows(DataIntegrityViolationException.class,()->new TransactionTemplate(transactions).execute(status->{
      String id=service.submit(json.valueToTree(Arrays.asList(item("E1",1))),student).get(0);
      // Failure is confined to this transaction's newly inserted test row, never existing data.
      db.update("UPDATE REQUEST_ITEM SET ITEM_KIND='X' WHERE REQUEST_ITEM_ID=?",Long.valueOf(id));return null;
    }));
    assertEquals(requests,db.queryForObject("SELECT COUNT(*) FROM RENT_REQUEST",Integer.class));assertEquals(items,db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM",Integer.class));assertEquals(logs,db.queryForObject("SELECT COUNT(*) FROM REQUEST_STATUS_LOG",Integer.class));
  }
}
