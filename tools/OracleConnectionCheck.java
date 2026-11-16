import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import java.util.regex.*;

/** Connection check ONLY: the sole SQL statement is the fixed SELECT 1 FROM DUAL. */
public final class OracleConnectionCheck {
  private static final String LOCAL_FILE = "src/main/resources/application-local.properties";
  private static final Pattern SID_URL = Pattern.compile("^jdbc:oracle:thin:@([A-Za-z0-9.-]+):([0-9]{1,5}):([A-Za-z0-9_]+)$");
  private static String option(Properties config,String key,String env) {
    String value=System.getenv(env);
    return value!=null ? value : config.getProperty(key,"");
  }
  private static Properties load() throws IOException {
    Properties config=new Properties();
    Path path=Paths.get(LOCAL_FILE);
    if(Files.isRegularFile(path))try(Reader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){config.load(reader);}
    return config;
  }
  public static void main(String[] args) {
    String mode=args.length==0?"--query":args[0];
    if(args.length>1||!(mode.equals("--query")||mode.equals("--tcp")||mode.equals("--check-config"))){
      System.err.println("Usage: OracleConnectionCheck [--query|--tcp|--check-config]");System.exit(2);
    }
    try {
      Properties config=load();
      String url=option(config,"spring.datasource.url","CAMPUSRENT_DB_URL").trim();
      String username=option(config,"spring.datasource.username","CAMPUSRENT_DB_USERNAME").trim();
      String password=option(config,"spring.datasource.password","CAMPUSRENT_DB_PASSWORD");
      Matcher address=SID_URL.matcher(url);
      if(!address.matches()){
        System.err.println("CONFIG_ERROR: expected a credential-free jdbc:oracle:thin:@host:port:SID URL.");System.exit(2);return;
      }
      int port=Integer.parseInt(address.group(2));
      if(port<1||port>65535){System.err.println("CONFIG_ERROR: invalid TCP port.");System.exit(2);return;}
      if(mode.equals("--tcp")){
        try(Socket socket=new Socket()) {socket.connect(new InetSocketAddress(address.group(1),port),5000);}
        System.out.println("TCP_PASS: destination port reachable; Oracle authentication and query NOT tested.");return;
      }
      if(username.isEmpty()||password.isEmpty()){
        System.err.println("CONFIG_REQUIRED: enter DB credentials in the ignored application-local.properties or CAMPUSRENT_DB_USERNAME/PASSWORD. No DB login attempted.");
        System.exit(2);return;
      }
      if(mode.equals("--check-config")){
        System.out.println("CONFIG_READY: URL and credentials supplied; values are not printed. No network access.");return;
      }
      Class.forName("oracle.jdbc.OracleDriver");
      Properties connectionProperties=new Properties();
      connectionProperties.setProperty("user",username);
      connectionProperties.setProperty("password",password);
      connectionProperties.setProperty("oracle.net.CONNECT_TIMEOUT","5000");
      connectionProperties.setProperty("oracle.jdbc.ReadTimeout","10000");
      DriverManager.setLoginTimeout(15);
      try(Connection connection=DriverManager.getConnection(url,connectionProperties)) {
        connection.setAutoCommit(false);
        try(Statement statement=connection.createStatement()) {
          statement.setQueryTimeout(10);
          try(ResultSet result=statement.executeQuery("SELECT 1 FROM DUAL")) {
            if(!result.next()||result.getInt(1)!=1)throw new SQLException("Unexpected result",null,0);
            System.out.println("QUERY_PASS: SELECT 1 FROM DUAL returned 1.");
          }
        } finally {connection.rollback();}
      }
    } catch(SQLException error) {
      // Never print vendor exception text, connection properties, stack traces or credentials.
      System.err.println("DB_TEST_FAILED: Oracle error code="+error.getErrorCode()+"; no credentials printed.");
      System.exit(1);
    } catch(ClassNotFoundException error) {
      System.err.println("DRIVER_MISSING: run tools/setup-oracle-jdbc.ps1 first.");System.exit(2);
    } catch(IOException error) {
      System.err.println(mode.equals("--tcp")?"TCP_FAILED: destination unreachable or local/network access denied.":"CONFIG_ERROR: cannot read the local configuration file.");System.exit(1);
    } catch(Exception error) {
      System.err.println("CHECK_FAILED: local configuration or runtime error; details suppressed to protect credentials.");System.exit(1);
    }
  }
}
