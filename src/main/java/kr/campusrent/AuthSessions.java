package kr.campusrent;
import java.util.*;import javax.servlet.http.*;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;import org.springframework.context.annotation.Bean;import org.springframework.stereotype.Component;
@Component
public class AuthSessions implements HttpSessionListener {
 final Map<String,HttpSession> sessions=new HashMap<>();
 public synchronized void track(HttpSession session){sessions.values().removeIf(s->s==session);sessions.put(session.getId(),session);}
 public synchronized void sessionDestroyed(HttpSessionEvent event){sessions.values().removeIf(s->s==event.getSession());}
 public synchronized void invalidateUser(Object id){for(HttpSession s:new ArrayList<>(sessions.values()))try{Object u=s.getAttribute("user");if(u instanceof Map&&id.toString().equals(String.valueOf(((Map<?,?>)u).get("id"))))s.invalidate();}catch(IllegalStateException ignored){}}
 @Bean ServletListenerRegistrationBean<AuthSessions> sessionListener(){return new ServletListenerRegistrationBean<>(this);}
}
