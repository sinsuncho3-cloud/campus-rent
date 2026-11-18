package kr.campusrent;
import java.util.*;
import javax.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class WebConfig implements WebMvcConfigurer {
  public void addViewControllers(ViewControllerRegistry registry){
    for(String path:Arrays.asList("/","/equipment","/facilities","/purpose","/records","/history","/admin","/admin/recommendations","/admin/models","/admin/equipment","/admin/alternatives","/admin/facilities"))registry.addViewController(path).setViewName("forward:/screen");
  }
  public void addInterceptors(InterceptorRegistry registry){
    registry.addInterceptor(new HandlerInterceptor(){
      public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler)throws Exception{
        res.setHeader("Cache-Control","no-store");
        res.setHeader("X-Content-Type-Options","nosniff");
        if(!"GET".equals(req.getMethod())){
          HttpSession session=req.getSession(false);
          String token=req.getHeader("X-CSRF-Token");
          if(session==null||token==null||!token.equals(session.getAttribute("csrf"))){res.setStatus(403);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"세션을 새로고침한 뒤 다시 시도해주세요.\"}");return false;}
        }
        return true;
      }
    }).addPathPatterns("/api/**");
  }
}
