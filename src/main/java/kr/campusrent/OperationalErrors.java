package kr.campusrent;
import javax.servlet.http.HttpServletRequest;import org.springframework.dao.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.transaction.*;
@RestControllerAdvice
public class OperationalErrors {
 final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(OperationalErrors.class);
 @ExceptionHandler({DataAccessException.class,TransactionException.class})
 public ResponseEntity<?> database(Exception e,HttpServletRequest request){
  boolean conflict=e instanceof ConcurrencyFailureException||e instanceof DataIntegrityViolationException;
  int status=conflict?409:e instanceof QueryTimeoutException?504:503;
  String ref=java.util.UUID.randomUUID().toString();log.warn("database_failure reference={} category={} operation={}",ref,e.getClass().getSimpleName(),request.getMethod());
  String message=conflict?"다른 처리와 충돌했습니다. 최신 상태를 조회한 뒤 다시 확인해주세요.":"DB 응답을 확인할 수 없습니다. 저장 요청은 처리되었을 수 있으니 신청·처리 내역을 조회한 뒤 재시도해주세요.";
  return ResponseEntity.status(status).header("X-Request-Id",ref).body(RentalService.obj("message",message,"reference",ref,"code",conflict?"CONFLICT":"DATABASE_UNAVAILABLE"));
 }
 @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
 public ResponseEntity<?> malformed(){return ResponseEntity.badRequest().body(RentalService.obj("message","입력 형식을 확인해주세요."));}
}
