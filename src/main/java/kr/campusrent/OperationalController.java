package kr.campusrent;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestController
public class OperationalController {
 final JdbcTemplate db;public OperationalController(JdbcTemplate db){this.db=db;}
 @GetMapping("/api/health/live") public Object live(){return RentalService.obj("status","UP");}
 @GetMapping("/api/health/ready") public ResponseEntity<?> ready(){try{db.queryForObject("SELECT 1 FROM DUAL",Integer.class);return ResponseEntity.ok(RentalService.obj("status","UP"));}catch(org.springframework.dao.DataAccessException e){return ResponseEntity.status(503).body(RentalService.obj("status","DOWN"));}}
}
