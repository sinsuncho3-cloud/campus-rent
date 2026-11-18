package kr.campusrent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import javax.servlet.http.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces="application/json;charset=UTF-8")
public class ApiController {
  final RentalService service;
  final AdminService management;
  final ModelService models;
  @org.springframework.beans.factory.annotation.Autowired RecommendationService recommendations;
  @GetMapping("/api/recommendation-rules") public Object recommendationRules(){return recommendations.rules();}
  @GetMapping("/api/admin/recommendations") public Object recommendationManagement(HttpSession session){return recommendations.management(user(session));}
  @PostMapping({"/api/admin/recommendations/keywords","/api/admin/recommendations/keywords/{id}"}) public Object saveKeyword(@PathVariable(required=false) Long id,@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",recommendations.save(id,input,user(session)));}
  @org.springframework.beans.factory.annotation.Autowired LoginThrottle throttle;
  @org.springframework.beans.factory.annotation.Autowired AuthSessions sessions;
  @GetMapping("/api/client-config") public Object clientConfig(){return RentalService.obj("demoAccounts",service.demoAccountsEnabled());}
  @PostMapping("/api/account/password") public Object changePassword(@RequestBody JsonNode input,HttpSession session,HttpServletRequest req){Map<String,Object> who=user(session);RentalService.userId(who);throttle.attempt(String.valueOf(who.get("email")),req.getRemoteAddr());service.changePassword(input,who);sessions.invalidateUser(who.get("id"));try{session.invalidate();}catch(IllegalStateException ignored){}return RentalService.obj("ok",true);}
  @GetMapping("/api/admin/facilities/{id}/block-history") public Object blockHistory(@PathVariable long id,HttpSession session){return management.blockHistory(id,user(session));}
  public ApiController(RentalService service,AdminService management,ModelService models){this.service=service;this.management=management;this.models=models;}
  @GetMapping("/api/admin/models") public Object models(HttpSession session){return models.snapshot(user(session));}
  @PostMapping({"/api/admin/types","/api/admin/types/{id}"}) public Object saveType(@PathVariable(required=false) Long id,@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",models.saveType(id,input,user(session)));}
  @PostMapping({"/api/admin/spec-items","/api/admin/spec-items/{id}"}) public Object saveSpec(@PathVariable(required=false) Long id,@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",models.saveItem(id,input,user(session)));}
  @PostMapping({"/api/admin/models","/api/admin/models/{id}"}) public Object saveModel(@PathVariable(required=false) Long id,@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",models.saveModel(id,input,user(session)));}
  @PostMapping("/api/admin/facilities/{id}/blocks/{blockId}/cancel") public Object cancelBlock(@PathVariable long id,@PathVariable long blockId,@RequestBody JsonNode input,HttpSession session){management.cancelBlock(id,blockId,input,user(session));return RentalService.obj("ok",true);}
  @SuppressWarnings("unchecked") Map<String,Object> user(HttpSession session){return (Map<String,Object>)session.getAttribute("user");}
  Map<String,Object> identity(HttpSession session){if(session.getAttribute("csrf")==null)session.setAttribute("csrf",UUID.randomUUID().toString());return RentalService.obj("user",user(session),"csrf",session.getAttribute("csrf"));}
  @GetMapping(value="/screen",produces="text/html;charset=UTF-8") public ClassPathResource screen(){return new ClassPathResource("templates/index.html");}
  @GetMapping("/api/session") public Map<String,Object> session(HttpSession session){return identity(session);}
  @PostMapping("/api/login") public Map<String,Object> login(@RequestBody JsonNode input,HttpServletRequest req){throttle.attempt(input.path("email").asText(""),req.getRemoteAddr());Map<String,Object> profile=service.login(input);req.changeSessionId();req.getSession().setAttribute("user",profile);sessions.track(req.getSession());return identity(req.getSession());}
  @PostMapping("/api/signup") public Map<String,Object> signup(@RequestBody JsonNode input,HttpServletRequest req){throttle.attempt(input.path("email").asText(""),req.getRemoteAddr());Map<String,Object> profile=service.signup(input);req.changeSessionId();req.getSession().setAttribute("user",profile);sessions.track(req.getSession());return identity(req.getSession());}
  @PostMapping("/api/logout") public Map<String,Object> logout(HttpSession session){session.invalidate();return RentalService.obj("ok",true);}
  @GetMapping("/api/catalog") public Object catalog(){return service.catalog();}
  @GetMapping("/api/state") public Object state(HttpSession session,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size,@RequestParam(defaultValue="all") String view){return service.state(user(session),page,size,view);}
  @GetMapping("/api/requests/{id}/inspection-history") public Object inspectionHistory(@PathVariable long id,HttpSession session){return service.inspectionHistory(id,user(session));}
  @PostMapping("/api/requests") public Object submit(@RequestBody JsonNode input,HttpSession session){return input.hasNonNull("requestKey")?service.submitOnce(input.path("items"),input.path("requestKey").asText(),user(session)):service.submit(input.path("items"),user(session));}
  @PostMapping("/api/requests/{id}/transition") public Object transition(@PathVariable long id,@RequestBody JsonNode input,HttpSession session){service.transition(id,input,user(session));return RentalService.obj("ok",true);}
  @PostMapping("/api/equipment/{id}/inspection-complete") public Object inspection(@PathVariable String id,HttpSession session){service.finishInspection(id,user(session));return RentalService.obj("ok",true);}
  @GetMapping("/api/admin/manage") public Object management(HttpSession session){return management.snapshot(user(session));}
  @PostMapping("/api/admin/equipment") public Object createEquipment(@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",management.saveEquipment(null,input,user(session)));}
  @PostMapping("/api/admin/equipment/{id}") public Object editEquipment(@PathVariable long id,@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",management.saveEquipment(id,input,user(session)));}
  @PostMapping("/api/admin/alternatives") public Object saveAlternative(@RequestBody JsonNode input,HttpSession session){management.saveAlternative(input,user(session));return RentalService.obj("ok",true);}
  @PostMapping("/api/admin/facilities") public Object createFacility(@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",management.saveFacility(null,input,user(session)));}
  @PostMapping("/api/admin/facilities/{id}") public Object editFacility(@PathVariable long id,@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",management.saveFacility(id,input,user(session)));}
  @PostMapping("/api/admin/facilities/{id}/blocks") public Object createBlock(@PathVariable long id,@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",management.saveBlock(id,null,input,user(session)));}
  @PostMapping("/api/admin/facilities/{id}/blocks/{blockId}") public Object editBlock(@PathVariable long id,@PathVariable long blockId,@RequestBody JsonNode input,HttpSession session){return RentalService.obj("id",management.saveBlock(id,blockId,input,user(session)));}
  @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> bad(IllegalArgumentException error){return ResponseEntity.badRequest().body(RentalService.obj("message",error.getMessage()));}
  @ExceptionHandler(LoginThrottle.Limited.class) public ResponseEntity<?> limited(){return ResponseEntity.status(429).header("Retry-After","900").body(RentalService.obj("message","인증 시도가 많습니다. 15분 후 다시 시도해주세요."));}
}
