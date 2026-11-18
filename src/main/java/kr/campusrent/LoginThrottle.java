package kr.campusrent;
import java.time.Clock;import java.util.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;

/** Bounded, process-local attempt windows. No account names or IPs are written to logs. */
@Component
public class LoginThrottle {
 final Clock clock;final int accountLimit,ipLimit;final long window;
 final Map<String,long[]> accounts=new HashMap<>(),addresses=new HashMap<>();
 @org.springframework.beans.factory.annotation.Autowired
 public LoginThrottle(@Value("${campusrent.auth.account-limit:20}") int accountLimit,@Value("${campusrent.auth.ip-limit:100}") int ipLimit){this(Clock.systemUTC(),accountLimit,ipLimit,900000);}
 LoginThrottle(Clock clock,int account,int ip,long window){this.clock=clock;this.accountLimit=account;this.ipLimit=ip;this.window=window;}
 public synchronized void attempt(String email,String address){long now=clock.millis();prune(accounts,now);prune(addresses,now);String key=email.trim().toLowerCase(Locale.ROOT);if(key.length()>254)key="invalid";reserve(addresses,address,ipLimit,now);reserve(accounts,key,accountLimit,now);}
 void prune(Map<String,long[]> entries,long now){entries.entrySet().removeIf(e->now-e.getValue()[0]>=window);}
 void reserve(Map<String,long[]> entries,String key,int limit,long now){long[] value=entries.get(key);if(value==null){if(entries.size()>=10000)throw new Limited();value=new long[]{now,0};entries.put(key,value);}if(value[1]>=limit)throw new Limited();value[1]++;}
 static class Limited extends RuntimeException {}
}
