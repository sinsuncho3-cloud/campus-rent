package kr.campusrent;
import org.springframework.boot.ApplicationArguments;import org.springframework.boot.ApplicationRunner;import org.springframework.context.annotation.Profile;import org.springframework.core.env.Environment;import org.springframework.stereotype.Component;
@Component @Profile("production")
public class ProductionGuard implements ApplicationRunner {
 final Environment env;public ProductionGuard(Environment env){this.env=env;}
 public void run(ApplicationArguments args){if(!env.getProperty("campusrent.auth.block-demo-accounts",Boolean.class,false)||!env.getProperty("server.servlet.session.cookie.secure",Boolean.class,false)||!"127.0.0.1".equals(env.getProperty("server.address")))throw new IllegalStateException("Production requires demo-account blocking, secure cookies and loopback binding.");}
}
