package kr.campusrent;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalService {
  final JdbcTemplate db;
  final PasswordEncoder passwords;
  final String missingAccountHash;
  @org.springframework.beans.factory.annotation.Value("${campusrent.auth.block-demo-accounts:false}") boolean blockDemo;
  static final ZoneId KOREA=ZoneId.of("Asia/Seoul");
  static final List<String> CODES=Arrays.asList("PENDING","APPROVED","REJECTED","CANCELLED","CHECKED_OUT","RETURN_REQUESTED","RETURN_HOLD","RETURNED","IN_USE","COMPLETED");
  static final List<String> LABELS=Arrays.asList("승인 대기","승인","거절","취소","출고","반납 요청","반납 보류","반납 완료","이용 중","이용 완료");
  public RentalService(JdbcTemplate db){
    this.db=db;
    Map<String,PasswordEncoder> encoders=new HashMap<>();encoders.put("pbkdf2@SpringSecurity_v5_8",Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    passwords=new DelegatingPasswordEncoder("pbkdf2@SpringSecurity_v5_8",encoders);
    missingAccountHash=passwords.encode(UUID.randomUUID().toString());
  }
  static Map<String,Object> obj(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
  static void check(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
  static String text(JsonNode n,String key,int max){String s=n.path(key).asText("").trim();check(s.length()<=max,"입력 길이를 확인해주세요.");return s;}
  static long integer(JsonNode n,String key,long min,long max){JsonNode v=n.path(key);check(v.isIntegralNumber(),"정수 값을 입력해주세요.");long x=v.asLong();check(x>=min&&x<=max,"입력 수량·버전을 확인해주세요.");return x;}
  static long num(Map<String,Object> m,String key){return ((Number)m.get(key)).longValue();}
  static String str(Map<String,Object> m,String key){Object v=m.get(key);return v==null?"":v.toString();}
  static String date(Object v){return v==null?null:((Timestamp)v).toLocalDateTime().toString();}
  long next(String table){check(Arrays.asList("APP_USER","RENT_REQUEST","REQUEST_ITEM","REQUEST_STATUS_LOG","EQUIPMENT_CHECKOUT","FACILITY_USAGE","EQUIPMENT_CHECK_LOG").contains(table),"잘못된 식별자입니다.");return db.queryForObject("SELECT SQ_"+table+".NEXTVAL FROM DUAL",Long.class);}
  static long resourceNumber(String id){check(id!=null&&id.matches("[EF][1-9][0-9]{0,9}"),"자원 번호를 확인해주세요.");return Long.parseLong(id.substring(1));}
  static String resourceId(Map<String,Object> row){return "E".equals(str(row,"ITEM_KIND"))?"E"+num(row,"EQUIPMENT_ID"):"F"+num(row,"FACILITY_ID");}
  public boolean demoAccountsEnabled(){return !blockDemo;}
  @Transactional(readOnly=true)
  public Map<String,Object> login(JsonNode input){
    String email=text(input,"email",254).toLowerCase(Locale.ROOT),raw=input.path("password").asText("");
    check(raw.length()<=256,"이메일 또는 비밀번호를 확인해주세요.");
    check(!blockDemo||!Arrays.asList("demo@campusrent.example","other@campusrent.example","admin@campusrent.example").contains(email),"이메일 또는 비밀번호를 확인해주세요.");
    List<Map<String,Object>> rows=db.queryForList("SELECT USER_ID,EMAIL,PASSWORD,USER_NAME,ROLE_CODE FROM APP_USER WHERE EMAIL=?",email);
    boolean valid=false;
    try{valid=passwords.matches(raw,rows.size()==1?str(rows.get(0),"PASSWORD"):missingAccountHash)&&rows.size()==1;}catch(IllegalArgumentException ignored){}
    check(valid,"이메일 또는 비밀번호를 확인해주세요.");return profile(rows.get(0));
  }
  @Transactional
  public Map<String,Object> signup(JsonNode input){
    String email=text(input,"email",254).toLowerCase(Locale.ROOT),name=text(input,"name",50),raw=input.path("password").asText("");
    check(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")&&!name.isEmpty(),"이름과 이메일을 확인해주세요.");
    check(raw.length()>=12&&raw.length()<=256&&raw.matches("(?s).*[A-Za-z].*")&&raw.matches("(?s).*[0-9].*"),"비밀번호는 영문·숫자를 포함해 12자 이상 입력해주세요.");
    long id=next("APP_USER");
    try{db.update("INSERT INTO APP_USER(USER_ID,EMAIL,PASSWORD,USER_NAME,ROLE_CODE) VALUES(?,?,?,?,'STUDENT')",id,email,passwords.encode(raw),name);}catch(DuplicateKeyException e){throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");}
    return obj("id",String.valueOf(id),"name",name,"email",email,"role","student");
  }
  Map<String,Object> profile(Map<String,Object> r){return obj("id",String.valueOf(num(r,"USER_ID")),"email",str(r,"EMAIL"),"name",str(r,"USER_NAME"),"role",str(r,"ROLE_CODE").toLowerCase(Locale.ROOT));}
  @Transactional
  public void changePassword(JsonNode input,Map<String,Object> user){long id=userId(user);String current=input.path("currentPassword").asText(""),next=input.path("newPassword").asText("");check(current.length()<=256&&next.length()>=12&&next.length()<=256&&next.matches("(?s).*[A-Za-z].*")&&next.matches("(?s).*[0-9].*"),"새 비밀번호는 영문·숫자를 포함해 12자 이상 입력해주세요.");String hash=db.queryForObject("SELECT PASSWORD FROM APP_USER WHERE USER_ID=? FOR UPDATE WAIT 5",String.class,id);check(passwords.matches(current,hash),"현재 비밀번호를 확인해주세요.");check(!current.equals(next),"현재와 다른 비밀번호를 입력해주세요.");db.update("UPDATE APP_USER SET PASSWORD=? WHERE USER_ID=?",passwords.encode(next),id);}
  static long userId(Map<String,Object> user){check(user!=null,"로그인이 필요합니다.");return Long.parseLong(user.get("id").toString());}
  static void admin(Map<String,Object> user){userId(user);check("admin".equals(user.get("role")),"관리자 권한이 필요합니다.");}
  static void student(Map<String,Object> user){userId(user);check("student".equals(user.get("role")),"학생 계정으로 로그인해주세요.");}
  static String operationStatus(Object v){return "NORMAL".equals(v)?"정상":"CHECKING".equals(v)?"점검":"중지";}

  @Transactional(readOnly=true)
  public List<Map<String,Object>> catalog(){
    List<Map<String,Object>> result=new ArrayList<>();
    List<Map<String,Object>> components=db.queryForList("SELECT * FROM EQUIPMENT_COMPONENT ORDER BY DISPLAY_ORDER");
    List<Map<String,Object>> specs=db.queryForList("SELECT M.MODEL_ID,S.SPEC_NAME,M.SPEC_VALUE,M.VALUE_UNIT,M.VERIFY_STATUS FROM MODEL_SPEC M JOIN SPEC_ITEM S ON S.SPEC_ITEM_ID=M.SPEC_ITEM_ID ORDER BY S.DISPLAY_ORDER");
    List<Map<String,Object>> alternatives=db.queryForList("SELECT SOURCE_EQUIPMENT_ID,TARGET_EQUIPMENT_ID FROM EQUIPMENT_ALTERNATIVE WHERE ENABLED_YN='Y'");
    for(Map<String,Object> e:db.queryForList("SELECT E.*,M.MODEL_CODE,T.CATEGORY_LABEL,T.ENABLED_YN TYPE_ENABLED FROM EQUIPMENT E JOIN EQUIPMENT_MODEL M ON M.MODEL_ID=E.MODEL_ID JOIN EQUIPMENT_TYPE T ON T.TYPE_ID=M.TYPE_ID ORDER BY E.EQUIPMENT_ID")){
      long id=num(e,"EQUIPMENT_ID"),model=num(e,"MODEL_ID");String category=str(e,"CATEGORY_LABEL");
      List<Map<String,Object>> parts=new ArrayList<>();
      for(Map<String,Object> p:components)if(num(p,"EQUIPMENT_ID")==id)parts.add(obj("name",str(p,"COMPONENT_NAME"),"quantity",num(p,"REQUIRED_QTY")));
      List<String> specification=new ArrayList<>();for(Map<String,Object> s:specs)if(num(s,"MODEL_ID")==model)specification.add("CONFIRMED".equals(s.get("VERIFY_STATUS"))?str(s,"SPEC_NAME")+" "+str(s,"SPEC_VALUE")+str(s,"VALUE_UNIT"):"사양 확인 필요");
      List<String> alternate=new ArrayList<>();for(Map<String,Object> a:alternatives)if(num(a,"SOURCE_EQUIPMENT_ID")==id)alternate.add("E"+num(a,"TARGET_EQUIPMENT_ID"));
      result.add(obj("id","E"+id,"name",str(e,"EQUIPMENT_NAME"),"kind","장비","category",category,"code",str(e,"ASSET_CODE"),"model",str(e,"MODEL_CODE"),"location",str(e,"STORAGE_LOCATION"),"rentable","Y".equals(e.get("LEND_ALLOWED_YN"))&&"Y".equals(e.get("TYPE_ENABLED")),"status",operationStatus(e.get("OPERATING_STATUS")),"icon","카메라".equals(category)?"ff97a.svg":"마이크".equals(category)?"054e2.svg":"1eae3.svg","specs",specification,"components",parts,"contents",parts.stream().map(p->p.get("name")+" "+p.get("quantity")+"개").collect(Collectors.toList()),"alternatives",alternate));
    }
    List<Map<String,Object>> uses=db.queryForList("SELECT FP.FACILITY_ID,P.PURPOSE_NAME FROM FACILITY_PURPOSE FP JOIN USE_PURPOSE P ON P.PURPOSE_ID=FP.PURPOSE_ID WHERE P.ENABLED_YN='Y'");
    List<Map<String,Object>> allHours=db.queryForList("SELECT FACILITY_ID,WEEKDAY_NO,OPEN_TIME,CLOSE_TIME FROM FACILITY_HOURS");
    for(Map<String,Object> f:db.queryForList("SELECT * FROM FACILITY ORDER BY FACILITY_ID")){
      long id=num(f,"FACILITY_ID");List<String> allowed=new ArrayList<>();for(Map<String,Object> u:uses)if(num(u,"FACILITY_ID")==id)allowed.add(str(u,"PURPOSE_NAME"));
      Map<String,Object> hoursByDay=new LinkedHashMap<>();for(Map<String,Object> h:allHours)if(num(h,"FACILITY_ID")==id)hoursByDay.put(String.valueOf(num(h,"WEEKDAY_NO")),Arrays.asList(str(h,"OPEN_TIME"),str(h,"CLOSE_TIME")));
      result.add(obj("id","F"+id,"name",str(f,"FACILITY_NAME"),"kind","시설","category",str(f,"FACILITY_TYPE"),"code",str(f,"FACILITY_CODE"),"model","시설","location",str(f,"LOCATION_TEXT"),"capacity",num(f,"CAPACITY"),"rentable","Y".equals(f.get("USE_ALLOWED_YN")),"status",operationStatus(f.get("OPERATING_STATUS")),"icon","b46a5.svg","specs",Arrays.asList("수용 "+num(f,"CAPACITY")+"명","공간 전체 예약"),"contents",Arrays.asList(str(f,"FIXED_EQUIPMENT_INFO")),"allowedUses",allowed,"hoursByDay",hoursByDay,"alternatives",Collections.emptyList()));
    }
    return result;
  }
  Map<String,Object> condition(Map<String,Object> r){return obj("start",date(r.get("USE_START_AT")),"end",date(r.get("USE_END_AT")),"people",r.get("PEOPLE_COUNT")==null?1:num(r,"PEOPLE_COUNT"),"use",str(r,"PURPOSE_NAME"));}
  @Transactional(readOnly=true)
  public Map<String,Object> state(Map<String,Object> user){
    return state(user,0,50,"all");
  }
  @Transactional(readOnly=true)
  public Map<String,Object> state(Map<String,Object> user,int page,int size,String view){
    check(page>=0&&page<=100000&&size>=1&&size<=100&&Arrays.asList("all","current","history").contains(view),"조회 범위를 확인해주세요.");
    List<Map<String,Object>> records=new ArrayList<>(),reservations=new ArrayList<>();
    String where=user==null?" AND 1=0":"admin".equals(user.get("role"))?"":" AND R.APPLICANT_ID=?";
    Object[] args=user!=null&&!"admin".equals(user.get("role"))?new Object[]{userId(user)}:new Object[0];
    if(!"all".equals(view))where+=" AND I.STATUS_CODE "+("history".equals(view)?"IN":"NOT IN")+" ('REJECTED','CANCELLED','RETURNED','COMPLETED')";
    int total=db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM I JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID WHERE 1=1"+where,Integer.class,args);
    List<Object> pageArgs=new ArrayList<>(Arrays.asList(args));pageArgs.add((long)(page+1)*size);pageArgs.add((long)page*size);
    List<Long> selected=db.queryForList("SELECT REQUEST_ITEM_ID FROM (SELECT X.REQUEST_ITEM_ID,ROWNUM RN FROM (SELECT I.REQUEST_ITEM_ID FROM REQUEST_ITEM I JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID WHERE 1=1"+where+" ORDER BY I.REQUEST_ITEM_ID DESC) X WHERE ROWNUM<=?) WHERE RN>?",Long.class,pageArgs.toArray());
    where+=selected.isEmpty()?" AND 1=0":" AND I.REQUEST_ITEM_ID IN ("+selected.stream().map(String::valueOf).collect(Collectors.joining(","))+")";
    Map<Long,List<Map<String,Object>>> histories=new HashMap<>(),snapshots=new HashMap<>();
    for(Map<String,Object> l:db.queryForList("SELECT L.*,U.USER_NAME FROM REQUEST_STATUS_LOG L JOIN REQUEST_ITEM I ON I.REQUEST_ITEM_ID=L.REQUEST_ITEM_ID JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID JOIN APP_USER U ON U.USER_ID=L.CHANGED_BY WHERE 1=1"+where+" ORDER BY L.STATUS_LOG_ID",args))histories.computeIfAbsent(num(l,"REQUEST_ITEM_ID"),k->new ArrayList<>()).add(obj("from",label(str(l,"FROM_STATUS")),"to",label(str(l,"TO_STATUS")),"reason",str(l,"REASON_TEXT"),"at",date(l.get("CHANGED_AT")),"by",str(l,"USER_NAME")));
    for(Map<String,Object> p:db.queryForList("SELECT E.REQUEST_ITEM_ID,C.COMPONENT_SEQ,C.COMPONENT_NAME,C.DELIVERED_QTY,NVL(SUM(V.RETURNED_QTY),0) RETURNED_TOTAL FROM EQUIPMENT_CHECKOUT E JOIN REQUEST_ITEM I ON I.REQUEST_ITEM_ID=E.REQUEST_ITEM_ID JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID JOIN CHECKOUT_COMPONENT C ON C.CHECKOUT_ID=E.CHECKOUT_ID LEFT JOIN RETURN_COMPONENT V ON V.CHECKOUT_ID=C.CHECKOUT_ID AND V.COMPONENT_SEQ=C.COMPONENT_SEQ WHERE 1=1"+where+" GROUP BY E.REQUEST_ITEM_ID,C.COMPONENT_SEQ,C.COMPONENT_NAME,C.DELIVERED_QTY ORDER BY E.REQUEST_ITEM_ID,C.COMPONENT_SEQ",args))snapshots.computeIfAbsent(num(p,"REQUEST_ITEM_ID"),k->new ArrayList<>()).add(p);
    Map<Long,Map<String,Object>> originalSelections=new HashMap<>();
    for(Map<String,Object> a:db.queryForList("SELECT A.* FROM REQUEST_ALTERNATIVE A JOIN REQUEST_ITEM I ON I.REQUEST_ITEM_ID=A.REQUEST_ITEM_ID JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID WHERE 1=1"+where,args))originalSelections.put(num(a,"REQUEST_ITEM_ID"),a);
    for(Map<String,Object> r:db.queryForList("SELECT I.*,R.APPLICANT_ID,R.REQUESTED_AT,U.USER_NAME,P.PURPOSE_NAME FROM REQUEST_ITEM I JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID JOIN APP_USER U ON U.USER_ID=R.APPLICANT_ID LEFT JOIN USE_PURPOSE P ON P.PURPOSE_ID=I.PURPOSE_ID WHERE 1=1"+where+" ORDER BY I.REQUEST_ITEM_ID",args)){
      long id=num(r,"REQUEST_ITEM_ID");List<Map<String,Object>> logs=histories.getOrDefault(id,Collections.emptyList());
      Map<String,Object> record=obj("id",String.valueOf(id),"resourceId",resourceId(r),"condition",condition(r),"owner",String.valueOf(num(r,"APPLICANT_ID")),"ownerName",str(r,"USER_NAME"),"status",label(str(r,"STATUS_CODE")),"version",num(r,"ROW_VERSION"),"created",date(r.get("REQUESTED_AT")),"log",logs,"returned",new LinkedHashMap<String,Object>());
      record.put("originCode",str(r,"ORIGIN_CODE"));record.put("activity",str(r,"ACTIVITY_TEXT"));
      if(originalSelections.containsKey(id)){Map<String,Object> a=originalSelections.get(id);record.put("original",obj("resourceId",a.get("ORIGINAL_EQUIPMENT_ID")!=null?"E"+num(a,"ORIGINAL_EQUIPMENT_ID"):"F"+num(a,"ORIGINAL_FACILITY_ID"),"kind",str(a,"ALTERNATIVE_KIND"),"start",date(a.get("ORIGINAL_START_AT")),"end",date(a.get("ORIGINAL_END_AT"))));}
      if(snapshots.containsKey(id)){
        List<Map<String,Object>> parts=new ArrayList<>();Map<String,Object> returned=new LinkedHashMap<>();
        for(Map<String,Object> p:snapshots.get(id)){
          parts.add(obj("name",str(p,"COMPONENT_NAME"),"quantity",num(p,"DELIVERED_QTY")));returned.put(str(p,"COMPONENT_NAME"),num(p,"RETURNED_TOTAL"));
        }
        record.put("checkout",parts);record.put("returned",returned);
      }
      records.add(record);
    }
    for(Map<String,Object> r:db.queryForList("SELECT REQUEST_ITEM_ID,ITEM_KIND,EQUIPMENT_ID,FACILITY_ID,USE_START_AT,USE_END_AT,STATUS_CODE,PEOPLE_COUNT FROM REQUEST_ITEM WHERE STATUS_CODE IN ('APPROVED','CHECKED_OUT','RETURN_REQUESTED','RETURN_HOLD','IN_USE')"))reservations.add(obj("id","busy-"+num(r,"REQUEST_ITEM_ID"),"resourceId",resourceId(r),"condition",condition(r),"status",label(str(r,"STATUS_CODE"))));
    for(Map<String,Object> r:db.queryForList("SELECT BLOCK_ID,FACILITY_ID,BLOCK_START_AT,BLOCK_END_AT FROM FACILITY_BLOCK WHERE ENABLED_YN='Y'"))reservations.add(obj("id","block-"+num(r,"BLOCK_ID"),"resourceId","F"+num(r,"FACILITY_ID"),"condition",obj("start",date(r.get("BLOCK_START_AT")),"end",date(r.get("BLOCK_END_AT"))),"status","승인"));
    Map<String,Object> restrictions=new LinkedHashMap<>();for(Map<String,Object> r:db.queryForList("SELECT EQUIPMENT_ID FROM EQUIPMENT WHERE OPERATING_STATUS='CHECKING'"))restrictions.put("E"+num(r,"EQUIPMENT_ID"),"장비 점검 중입니다.");
    return obj("records",records,"reservations",reservations,"restrictions",restrictions,"pageInfo",obj("page",page,"size",size,"total",total,"view",view,"hasNext",(long)(page+1)*size<total));
  }
  static String label(String code){int i=CODES.indexOf(code);return i<0?"":LABELS.get(i);}
  static String code(String label){int i=LABELS.indexOf(label);check(i>=0,"잘못된 상태입니다.");return CODES.get(i);}
  Map<String,Object> lockResource(String resource){long id=resourceNumber(resource);return db.queryForMap(resource.startsWith("E")?"SELECT * FROM EQUIPMENT WHERE EQUIPMENT_ID=? FOR UPDATE WAIT 5":"SELECT * FROM FACILITY WHERE FACILITY_ID=? FOR UPDATE WAIT 5",id);}
  Timestamp time(JsonNode input,String key){try{String value=input.path(key).asText();LocalDateTime date=LocalDateTime.parse(value);check(date.getYear()>=2000&&date.getYear()<=2100,"날짜를 확인해주세요.");return Timestamp.valueOf(date);}catch(RuntimeException e){throw new IllegalArgumentException("날짜·시간을 확인해주세요.");}}
  Long purpose(String name){if(name.isEmpty())return null;List<Long> ids=db.queryForList("SELECT PURPOSE_ID FROM USE_PURPOSE WHERE PURPOSE_NAME=? AND ENABLED_YN='Y'",Long.class,name);check(ids.size()==1,"실제 이용 목적을 선택해주세요.");return ids.get(0);}
  void available(String resource,Timestamp start,Timestamp end,Long people,Long purpose,long exclude){
    check(end.after(start)&&start.toLocalDateTime().isAfter(LocalDateTime.now(KOREA)),"현재 이후의 올바른 이용 기간을 선택해주세요.");
    long id=resourceNumber(resource);boolean eq=resource.startsWith("E");
    Map<String,Object> r=db.queryForMap(eq?"SELECT LEND_ALLOWED_YN ALLOWED,OPERATING_STATUS FROM EQUIPMENT WHERE EQUIPMENT_ID=?":"SELECT USE_ALLOWED_YN ALLOWED,OPERATING_STATUS,CAPACITY FROM FACILITY WHERE FACILITY_ID=?",id);
    check("Y".equals(r.get("ALLOWED"))&&"NORMAL".equals(r.get("OPERATING_STATUS")),"현재 이용할 수 없는 자원입니다.");
    if(eq)check(db.queryForObject("SELECT COUNT(*) FROM EQUIPMENT E JOIN EQUIPMENT_MODEL M ON M.MODEL_ID=E.MODEL_ID JOIN EQUIPMENT_TYPE T ON T.TYPE_ID=M.TYPE_ID WHERE E.EQUIPMENT_ID=? AND T.ENABLED_YN='Y'",Integer.class,id)==1,"사용 중지된 장비 품목입니다.");
    else{
      check(people!=null&&people>=1&&people<=num(r,"CAPACITY")&&purpose!=null,"시설 인원과 목적을 확인해주세요.");
      check(db.queryForObject("SELECT COUNT(*) FROM FACILITY_PURPOSE WHERE FACILITY_ID=? AND PURPOSE_ID=?",Integer.class,id,purpose)==1,"시설의 허용 용도와 맞지 않습니다.");
      LocalDateTime a=start.toLocalDateTime(),b=end.toLocalDateTime();check(a.toLocalDate().equals(b.toLocalDate()),"시설은 같은 날짜 안에서 이용해주세요.");
      List<Map<String,Object>> hours=db.queryForList("SELECT OPEN_TIME,CLOSE_TIME FROM FACILITY_HOURS WHERE FACILITY_ID=? AND WEEKDAY_NO=?",id,a.getDayOfWeek().getValue());
      check(hours.size()==1,"시설 휴무일입니다.");
      check(!a.toLocalTime().isBefore(LocalTime.parse(str(hours.get(0),"OPEN_TIME")))&&!b.toLocalTime().isAfter(LocalTime.parse(str(hours.get(0),"CLOSE_TIME"))),"시설 운영 시간 밖입니다.");
      check(db.queryForObject("SELECT COUNT(*) FROM FACILITY_BLOCK WHERE ENABLED_YN='Y' AND FACILITY_ID=? AND BLOCK_START_AT<? AND BLOCK_END_AT>?",Integer.class,id,end,start)==0,"시설 이용 제한 시간과 겹칩니다.");
    }
    check(db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM WHERE "+(eq?"EQUIPMENT_ID":"FACILITY_ID")+"=? AND REQUEST_ITEM_ID<>? AND STATUS_CODE IN ('APPROVED','CHECKED_OUT','RETURN_REQUESTED','RETURN_HOLD','IN_USE') AND USE_START_AT<? AND USE_END_AT>?",Integer.class,id,exclude,end,start)==0,"승인된 예약과 이용 기간이 겹칩니다.");
  }
  void log(long item,String from,String to,long actor,String reason){db.update("INSERT INTO REQUEST_STATUS_LOG(STATUS_LOG_ID,REQUEST_ITEM_ID,FROM_STATUS,TO_STATUS,CHANGED_BY,REASON_TEXT) VALUES(?,?,?,?,?,?)",next("REQUEST_STATUS_LOG"),item,from,to,actor,reason);}
  @Transactional
  public List<String> submit(JsonNode items,Map<String,Object> user){
    student(user);check(items.isArray()&&items.size()>0&&items.size()<=50,"1~50개의 신청 항목을 선택해주세요.");
    TreeSet<String> locks=new TreeSet<>();for(JsonNode item:items){String id=text(item,"resourceId",20);resourceNumber(id);check(locks.add(id),"같은 자원을 중복 신청할 수 없습니다.");}for(String id:locks)lockResource(id);
    long request=next("RENT_REQUEST"),actor=userId(user);db.update("INSERT INTO RENT_REQUEST(REQUEST_ID,REQUEST_NO,APPLICANT_ID) VALUES(?,?,?)",request,"CR-"+request,actor);
    List<String> ids=new ArrayList<>();int number=0;
    for(JsonNode item:items){
      String resource=text(item,"resourceId",20);JsonNode c=item.path("condition");Timestamp start=time(c,"start"),end=time(c,"end");boolean eq=resource.startsWith("E");Long people=eq?null:integer(c,"people",1,999);Long purpose=purpose(text(c,"use",50));
      available(resource,start,end,people,purpose,-1);long id=next("REQUEST_ITEM");
      String origin=text(item,"originCode",10);if(origin.isEmpty())origin="DIRECT";check(Arrays.asList("DIRECT","PURPOSE","ALTERNATE").contains(origin),"선택 출처를 확인해주세요.");
      db.update("INSERT INTO REQUEST_ITEM(REQUEST_ITEM_ID,REQUEST_ID,ITEM_NO,ITEM_KIND,EQUIPMENT_ID,FACILITY_ID,USE_START_AT,USE_END_AT,PEOPLE_COUNT,PURPOSE_ID,ORIGIN_CODE,ACTIVITY_TEXT) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,request,++number,eq?"E":"F",eq?resourceNumber(resource):null,eq?null:resourceNumber(resource),start,end,people,purpose,origin,text(item,"activity",1000));
      if("ALTERNATE".equals(origin)){
        JsonNode original=item.path("original");String source=text(original,"resourceId",20);long sourceId=resourceNumber(source);check(source.charAt(0)==resource.charAt(0),"원래 자원 종류가 일치하지 않습니다.");
        Timestamp firstStart=time(original.path("condition"),"start"),firstEnd=time(original.path("condition"),"end");check(firstEnd.after(firstStart),"최초 희망 기간을 확인해주세요.");
        boolean otherTime=source.equals(resource);
        if(otherTime)check(firstEnd.getTime()-firstStart.getTime()==end.getTime()-start.getTime()&&(!firstStart.equals(start)||!firstEnd.equals(end)),"다른 일정은 같은 이용 길이를 유지해야 합니다.");
        else{check(firstStart.equals(start)&&firstEnd.equals(end),"대체 자원은 최초 희망 일정을 유지해야 합니다.");if(eq)check(db.queryForObject("SELECT COUNT(*) FROM EQUIPMENT_ALTERNATIVE WHERE SOURCE_EQUIPMENT_ID=? AND TARGET_EQUIPMENT_ID=? AND ENABLED_YN='Y'",Integer.class,sourceId,resourceNumber(resource))==1,"지정된 대체 관계를 확인해주세요.");}
        db.update("INSERT INTO REQUEST_ALTERNATIVE(REQUEST_ITEM_ID,ALTERNATIVE_KIND,ORIGINAL_EQUIPMENT_ID,ORIGINAL_FACILITY_ID,ORIGINAL_START_AT,ORIGINAL_END_AT) VALUES(?,?,?,?,?,?)",id,otherTime?"OTHER_TIME":"OTHER_RESOURCE",eq?sourceId:null,eq?null:sourceId,firstStart,firstEnd);
      }
      log(id,null,"PENDING",actor,null);ids.add(String.valueOf(id));
    }
    return ids;
  }
  @Transactional
  public List<String> submitOnce(JsonNode items,String key,Map<String,Object> user){
    student(user);check(key!=null&&key.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"),"신청 확인 키를 새로 생성해주세요.");
    java.util.UUID uuid=java.util.UUID.fromString(key);java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());String number="I-"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.array());
    db.queryForObject("SELECT USER_ID FROM APP_USER WHERE USER_ID=? FOR UPDATE WAIT 5",Long.class,userId(user));
    List<Map<String,Object>> prior=db.queryForList("SELECT * FROM RENT_REQUEST WHERE REQUEST_NO=?",number);
    if(!prior.isEmpty()){
      check(num(prior.get(0),"APPLICANT_ID")==userId(user),"신청 확인 키가 충돌했습니다. 새로 조회해주세요.");
      List<Map<String,Object>> saved=db.queryForList("SELECT I.*,P.PURPOSE_NAME,A.ORIGINAL_EQUIPMENT_ID,A.ORIGINAL_FACILITY_ID,A.ORIGINAL_START_AT,A.ORIGINAL_END_AT FROM REQUEST_ITEM I LEFT JOIN USE_PURPOSE P ON P.PURPOSE_ID=I.PURPOSE_ID LEFT JOIN REQUEST_ALTERNATIVE A ON A.REQUEST_ITEM_ID=I.REQUEST_ITEM_ID WHERE I.REQUEST_ID=? ORDER BY I.ITEM_NO",num(prior.get(0),"REQUEST_ID"));
      check(items.isArray()&&items.size()==saved.size(),"같은 신청 확인 키의 내용이 변경되었습니다.");List<String> ids=new ArrayList<>();int n=0;
      for(Map<String,Object> r:saved){JsonNode item=items.get(n++),c=item.path("condition");String origin=text(item,"originCode",10);if(origin.isEmpty())origin="DIRECT";
        check(resourceId(r).equals(text(item,"resourceId",20))&&r.get("USE_START_AT").equals(time(c,"start"))&&r.get("USE_END_AT").equals(time(c,"end"))&&origin.equals(str(r,"ORIGIN_CODE"))&&text(item,"activity",1000).equals(str(r,"ACTIVITY_TEXT")),"같은 신청 확인 키의 내용이 변경되었습니다.");
        if("F".equals(str(r,"ITEM_KIND")))check(num(r,"PEOPLE_COUNT")==integer(c,"people",1,999)&&str(r,"PURPOSE_NAME").equals(text(c,"use",50)),"같은 신청 확인 키의 조건이 변경되었습니다.");
        if("ALTERNATE".equals(origin)){JsonNode a=item.path("original");String original=r.get("ORIGINAL_EQUIPMENT_ID")!=null?"E"+num(r,"ORIGINAL_EQUIPMENT_ID"):"F"+num(r,"ORIGINAL_FACILITY_ID");check(original.equals(text(a,"resourceId",20))&&r.get("ORIGINAL_START_AT").equals(time(a.path("condition"),"start"))&&r.get("ORIGINAL_END_AT").equals(time(a.path("condition"),"end")),"원래 선택 조건이 변경되었습니다.");}ids.add(String.valueOf(num(r,"REQUEST_ITEM_ID")));
      }return ids;
    }
    List<String> ids=submit(items,user);db.update("UPDATE RENT_REQUEST SET REQUEST_NO=? WHERE REQUEST_ID=(SELECT REQUEST_ID FROM REQUEST_ITEM WHERE REQUEST_ITEM_ID=?)",number,Long.valueOf(ids.get(0)));return ids;
  }
  @Transactional
  public void transition(long id,JsonNode input,Map<String,Object> user){
    long actor=userId(user);String to=code(text(input,"target",30));long expected=integer(input,"version",1,9999999999L);JsonNode details=input.path("details");String reason=text(details,"reason",1000);
    Map<String,Object> initial=db.queryForMap("SELECT * FROM REQUEST_ITEM WHERE REQUEST_ITEM_ID=?",id);String resource=resourceId(initial);lockResource(resource);
    Map<String,Object> r=db.queryForMap("SELECT I.*,R.APPLICANT_ID FROM REQUEST_ITEM I JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID WHERE I.REQUEST_ITEM_ID=? FOR UPDATE OF I.ROW_VERSION WAIT 5",id);
    check(resource.equals(resourceId(r)),"자원이 변경되었습니다. 다시 조회해주세요.");check(num(r,"ROW_VERSION")==expected,"다른 요청에서 변경된 내역입니다. 새로고침 후 다시 처리해주세요.");
    String from=str(r,"STATUS_CODE");boolean eq="E".equals(str(r,"ITEM_KIND"));
    if(Arrays.asList("CANCELLED","RETURN_REQUESTED").contains(to)){student(user);check(num(r,"APPLICANT_ID")==actor,"본인의 신청만 처리할 수 있습니다.");}else admin(user);
    boolean allowed="PENDING".equals(from)&&Arrays.asList("APPROVED","REJECTED","CANCELLED").contains(to)||"APPROVED".equals(from)&&(eq?"CHECKED_OUT":"IN_USE").equals(to)||eq&&"CHECKED_OUT".equals(from)&&"RETURN_REQUESTED".equals(to)||eq&&Arrays.asList("RETURN_REQUESTED","RETURN_HOLD").contains(from)&&Arrays.asList("RETURN_HOLD","RETURNED").contains(to)||!eq&&"IN_USE".equals(from)&&"COMPLETED".equals(to);
    check(allowed,"현재 상태에서 처리할 수 없는 작업입니다.");if(Arrays.asList("REJECTED","RETURN_HOLD").contains(to))check(!reason.isEmpty(),"처리 사유를 입력해주세요.");
    if("APPROVED".equals(to))available(resource,(Timestamp)r.get("USE_START_AT"),(Timestamp)r.get("USE_END_AT"),r.get("PEOPLE_COUNT")==null?null:num(r,"PEOPLE_COUNT"),r.get("PURPOSE_ID")==null?null:num(r,"PURPOSE_ID"),id);
    if("CHECKED_OUT".equals(to))checkout(id,resource,actor,details);
    if(Arrays.asList("RETURNED","RETURN_HOLD").contains(to))inspect(id,resource,actor,to,reason,details);
    if("IN_USE".equals(to)){
      Map<String,Object> f=db.queryForMap("SELECT * FROM FACILITY WHERE FACILITY_ID=?",resourceNumber(resource));check("NORMAL".equals(f.get("OPERATING_STATUS"))&&"Y".equals(f.get("USE_ALLOWED_YN")),"현재 운영 중인 시설이 아닙니다.");
      check(db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM WHERE FACILITY_ID=? AND STATUS_CODE='IN_USE'",Integer.class,resourceNumber(resource))==0,"이미 이용 중인 시설입니다.");
      db.update("INSERT INTO FACILITY_USAGE(USAGE_ID,REQUEST_ITEM_ID,ITEM_KIND,STARTED_AT,STARTED_BY) VALUES(?,?,'F',LOCALTIMESTAMP,?)",next("FACILITY_USAGE"),id,actor);
    }
    if("COMPLETED".equals(to))check(db.update("UPDATE FACILITY_USAGE SET ENDED_AT=LOCALTIMESTAMP,ENDED_BY=? WHERE REQUEST_ITEM_ID=? AND ENDED_AT IS NULL",actor,id)==1,"시설 이용 기록을 확인해주세요.");
    check(db.update("UPDATE REQUEST_ITEM SET STATUS_CODE=?,ROW_VERSION=ROW_VERSION+1,UPDATED_AT=LOCALTIMESTAMP WHERE REQUEST_ITEM_ID=? AND ROW_VERSION=?",to,id,expected)==1,"변경 충돌입니다.");log(id,from,to,actor,reason);
  }
  void checkout(long id,String resource,long actor,JsonNode details){
    check(details.path("confirmed").asBoolean(false),"출고 구성품을 확인해주세요.");
    Map<String,Object> e=db.queryForMap("SELECT * FROM EQUIPMENT WHERE EQUIPMENT_ID=?",resourceNumber(resource));
    check("NORMAL".equals(e.get("OPERATING_STATUS"))&&"Y".equals(e.get("LEND_ALLOWED_YN")),"점검·운영 중지 장비입니다.");
    check(db.queryForObject("SELECT COUNT(*) FROM REQUEST_ITEM WHERE EQUIPMENT_ID=? AND STATUS_CODE IN ('CHECKED_OUT','RETURN_REQUESTED','RETURN_HOLD')",Integer.class,resourceNumber(resource))==0,"기존 대여 장비가 아직 반납되지 않았습니다.");
    List<Map<String,Object>> parts=db.queryForList("SELECT * FROM EQUIPMENT_COMPONENT WHERE EQUIPMENT_ID=? ORDER BY DISPLAY_ORDER",resourceNumber(resource));check(!parts.isEmpty(),"기본 구성품을 등록해주세요.");
    String source=text(details,"source",20);if(source.isEmpty())source="ACTUAL";check(Arrays.asList("ACTUAL","BACKFILL").contains(source),"출고 기록 방식을 확인해주세요.");
    Timestamp actual=Timestamp.valueOf(LocalDateTime.now(KOREA));
    if("BACKFILL".equals(source)){
      check(!text(details,"reason",1000).isEmpty(),"사후 출고 등록 사유를 입력해주세요.");actual=time(details,"actualAt");check(!actual.toLocalDateTime().isAfter(LocalDateTime.now(KOREA)),"실제 출고 시각은 현재 이전이어야 합니다.");
      check(db.queryForObject("SELECT COUNT(*) FROM EQUIPMENT_CHECKOUT C JOIN REQUEST_ITEM I ON I.REQUEST_ITEM_ID=C.REQUEST_ITEM_ID WHERE I.EQUIPMENT_ID=? AND C.ACTUAL_CHECKOUT_AT IS NOT NULL AND NVL((SELECT MAX(R.INSPECTED_AT) FROM RETURN_INSPECTION R WHERE R.CHECKOUT_ID=C.CHECKOUT_ID AND R.OUTCOME_CODE='COMPLETE'),LOCALTIMESTAMP)>?",Integer.class,resourceNumber(resource),actual)==0,"입력한 출고 시각이 기존 실제 대여 기록과 겹칩니다.");
      check(details.path("parts").isArray()&&details.path("parts").size()==parts.size(),"당시 전달한 구성품과 수량을 모두 확인해주세요.");
    }
    long checkout=next("EQUIPMENT_CHECKOUT");db.update("INSERT INTO EQUIPMENT_CHECKOUT(CHECKOUT_ID,REQUEST_ITEM_ID,ITEM_KIND,ASSET_CODE_SNAPSHOT,EQUIPMENT_NAME_SNAPSHOT,SOURCE_CODE,ACTUAL_CHECKOUT_AT,REGISTERED_BY) VALUES(?,?,'E',?,?,?,?,?)",checkout,id,e.get("ASSET_CODE"),e.get("EQUIPMENT_NAME"),source,actual,actor);
    int seq=0;for(Map<String,Object> p:parts){long delivered=num(p,"REQUIRED_QTY");String condition="NORMAL",note="";if("BACKFILL".equals(source)){JsonNode part=details.path("parts").get(seq);check(str(p,"COMPONENT_NAME").equals(text(part,"name",100)),"구성품 기준이 변경되었습니다. 새로 조회해주세요.");delivered=integer(part,"quantity",0,99999);condition=text(part,"condition",10);note=text(part,"note",500);check(Arrays.asList("NORMAL","DAMAGED","MISSING").contains(condition)&&((delivered==0)=="MISSING".equals(condition)),"출고 수량과 구성품 상태를 확인해주세요.");check(!"Y".equals(p.get("BODY_YN"))||delivered>0,"본체 전달 수량은 1개 이상이어야 합니다.");check("NORMAL".equals(condition)||!note.isEmpty(),"손상·미전달 구성품의 메모를 입력해주세요.");}
      db.update("INSERT INTO CHECKOUT_COMPONENT(CHECKOUT_ID,COMPONENT_SEQ,COMPONENT_NAME,REQUIRED_QTY,DELIVERED_QTY,BODY_YN,REQUIRED_YN,OUT_CONDITION,NOTE_TEXT) VALUES(?,?,?,?,?,?,?,?,?)",checkout,++seq,p.get("COMPONENT_NAME"),p.get("REQUIRED_QTY"),delivered,p.get("BODY_YN"),p.get("REQUIRED_YN"),condition,note);
    }
  }
  void inspect(long id,String resource,long actor,String target,String reason,JsonNode details){
    long checkout=db.queryForObject("SELECT CHECKOUT_ID FROM EQUIPMENT_CHECKOUT WHERE REQUEST_ITEM_ID=? FOR UPDATE WAIT 5",Long.class,id);
    List<Map<String,Object>> parts=db.queryForList("SELECT C.*,NVL((SELECT SUM(R.RETURNED_QTY) FROM RETURN_COMPONENT R WHERE R.CHECKOUT_ID=C.CHECKOUT_ID AND R.COMPONENT_SEQ=C.COMPONENT_SEQ),0) TOTAL FROM CHECKOUT_COMPONENT C WHERE C.CHECKOUT_ID=? ORDER BY COMPONENT_SEQ",checkout);
    check(!parts.isEmpty(),"출고 구성품 기록이 없습니다.");JsonNode quantities=details.path("quantities");check(quantities.isObject(),"이번 반환 수량을 입력해주세요.");
    for(Map<String,Object> p:parts){long amount=integer(quantities,str(p,"COMPONENT_NAME"),0,99999),sum=num(p,"TOTAL")+amount;check(sum<=num(p,"DELIVERED_QTY"),"누적 반환 수량이 출고 수량을 초과합니다.");if("RETURNED".equals(target))check(sum==num(p,"DELIVERED_QTY"),"미반납 구성품이 있습니다. 보류 후 추가 반환을 확인해주세요.");}
    int round=db.queryForObject("SELECT NVL(MAX(INSPECTION_ROUND),0)+1 FROM RETURN_INSPECTION WHERE CHECKOUT_ID=?",Integer.class,checkout);check(round<=999,"반납 회차 한도를 초과했습니다.");
    db.update("INSERT INTO RETURN_INSPECTION(CHECKOUT_ID,INSPECTION_ROUND,INSPECTED_BY,OUTCOME_CODE,HOLD_REASON) VALUES(?,?,?,?,?)",checkout,round,actor,"RETURNED".equals(target)?"COMPLETE":"HOLD",reason);
    boolean damaged=false;
    for(Map<String,Object> p:parts){String name=str(p,"COMPONENT_NAME");long amount=quantities.path(name).asLong();JsonNode part=details.path("components").path(name);String condition=text(part,"condition",20),note=text(part,"note",500);if(condition.isEmpty())condition=amount==0&&num(p,"TOTAL")<num(p,"DELIVERED_QTY")?"MISSING":"NORMAL";
      check(Arrays.asList("NORMAL","DAMAGED","MISSING").contains(condition),"구성품 상태를 확인해주세요.");check(!"MISSING".equals(condition)||amount==0,"미반납 상태의 이번 반환 수량은 0이어야 합니다.");check(!"DAMAGED".equals(condition)||amount>0,"손상 상태는 이번에 반환받은 구성품에 지정해주세요.");check(!"DAMAGED".equals(condition)||!note.isEmpty(),"손상 구성품의 메모를 입력해주세요.");damaged|="DAMAGED".equals(condition);
      db.update("INSERT INTO RETURN_COMPONENT(CHECKOUT_ID,INSPECTION_ROUND,COMPONENT_SEQ,RETURNED_QTY,RETURN_CONDITION,NOTE_TEXT) VALUES(?,?,?,?,?,?)",checkout,round,p.get("COMPONENT_SEQ"),amount,condition,note);
    }
    boolean pastDamage=db.queryForObject("SELECT COUNT(*) FROM RETURN_COMPONENT WHERE CHECKOUT_ID=? AND RETURN_CONDITION='DAMAGED'",Integer.class,checkout)>0;
    if((damaged||pastDamage||details.path("needsInspection").asBoolean(false))&&!"CHECKING".equals(db.queryForObject("SELECT OPERATING_STATUS FROM EQUIPMENT WHERE EQUIPMENT_ID=?",String.class,resourceNumber(resource))))changeEquipment(resource,id,actor,"START_CHECK","CHECKING","반납 구성품 손상 또는 점검 요청");
  }
  @Transactional(readOnly=true)
  public Map<String,Object> inspectionHistory(long id,Map<String,Object> user){
    long actor=userId(user);Map<String,Object> owner=db.queryForMap("SELECT R.APPLICANT_ID FROM REQUEST_ITEM I JOIN RENT_REQUEST R ON R.REQUEST_ID=I.REQUEST_ID WHERE I.REQUEST_ITEM_ID=?",id);check("admin".equals(user.get("role"))||actor==num(owner,"APPLICANT_ID"),"본인의 검수 이력만 조회할 수 있습니다.");
    List<Map<String,Object>> checkout=db.queryForList("SELECT C.*,U.USER_NAME FROM EQUIPMENT_CHECKOUT C JOIN APP_USER U ON U.USER_ID=C.REGISTERED_BY WHERE REQUEST_ITEM_ID=?",id);if(checkout.isEmpty())return obj("checkout",null,"rounds",Collections.emptyList());long co=num(checkout.get(0),"CHECKOUT_ID");
    List<Map<String,Object>> rounds=db.queryForList("SELECT R.*,U.USER_NAME FROM RETURN_INSPECTION R JOIN APP_USER U ON U.USER_ID=R.INSPECTED_BY WHERE CHECKOUT_ID=? ORDER BY INSPECTION_ROUND",co);
    List<Map<String,Object>> rows=db.queryForList("SELECT R.*,C.COMPONENT_NAME,C.DELIVERED_QTY FROM RETURN_COMPONENT R JOIN CHECKOUT_COMPONENT C ON C.CHECKOUT_ID=R.CHECKOUT_ID AND C.COMPONENT_SEQ=R.COMPONENT_SEQ WHERE R.CHECKOUT_ID=? ORDER BY R.INSPECTION_ROUND,R.COMPONENT_SEQ",co);
    Map<String,Long> total=new HashMap<>();for(Map<String,Object> round:rounds){List<Map<String,Object>> own=new ArrayList<>();for(Map<String,Object> row:rows)if(num(row,"INSPECTION_ROUND")==num(round,"INSPECTION_ROUND")){String name=str(row,"COMPONENT_NAME");long sum=total.getOrDefault(name,0L)+num(row,"RETURNED_QTY");total.put(name,sum);row.put("CUMULATIVE_QTY",sum);own.add(row);}round.put("components",own);}
    return obj("checkout",checkout.get(0),"parts",db.queryForList("SELECT * FROM CHECKOUT_COMPONENT WHERE CHECKOUT_ID=? ORDER BY COMPONENT_SEQ",co),"rounds",rounds,"checkoutLog",db.queryForList("SELECT REASON_TEXT,CHANGED_AT FROM REQUEST_STATUS_LOG WHERE REQUEST_ITEM_ID=? AND TO_STATUS='CHECKED_OUT' ORDER BY STATUS_LOG_ID",id));
  }
  void changeEquipment(String resource,Long item,long actor,String action,String status,String reason){
    long id=resourceNumber(resource);String previous=db.queryForObject("SELECT OPERATING_STATUS FROM EQUIPMENT WHERE EQUIPMENT_ID=?",String.class,id);
    db.update("UPDATE EQUIPMENT SET OPERATING_STATUS=?,ROW_VERSION=ROW_VERSION+1 WHERE EQUIPMENT_ID=?",status,id);
    db.update("INSERT INTO EQUIPMENT_CHECK_LOG(CHECK_LOG_ID,EQUIPMENT_ID,RELATED_ITEM_ID,ACTION_CODE,FROM_STATUS,TO_STATUS,REASON_TEXT,CHECKED_BY) VALUES(?,?,?,?,?,?,?,?)",next("EQUIPMENT_CHECK_LOG"),id,item,action,previous,status,reason,actor);
  }
  @Transactional
  public void finishInspection(String resource,Map<String,Object> user){admin(user);check(resource.startsWith("E"),"장비만 점검 처리할 수 있습니다.");Map<String,Object> e=lockResource(resource);check("CHECKING".equals(e.get("OPERATING_STATUS")),"점검 상태가 변경되었습니다. 새로 조회해주세요.");changeEquipment(resource,null,userId(user),"END_CHECK","NORMAL","점검 완료");}
}
