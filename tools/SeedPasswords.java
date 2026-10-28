import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Generates and verifies only the PUBLIC DEVELOPMENT fixture accounts, not production credentials. */
public class SeedPasswords {
  static final String PREFIX = "{pbkdf2@SpringSecurity_v5_8}";
  static final String[][] USERS = {
    {"1","admin@campusrent.example","CampusAdmin123!","시연 관리자","ADMIN"},
    {"2","demo@campusrent.example","Campus123!","시연 사용자","STUDENT"},
    {"3","other@campusrent.example","Campus123!","다른 사용자","STUDENT"}
  };
  static byte[] derive(String password, byte[] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),salt,310000,256);
    try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
    finally { spec.clearPassword(); }
  }
  static String hex(byte[] bytes) { StringBuilder s=new StringBuilder(); for(byte b:bytes)s.append(String.format("%02x",b & 255)); return s.toString(); }
  static byte[] unhex(String value) { byte[] b=new byte[value.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return b; }
  static boolean matches(String password,String encoded) throws Exception {
    if(!encoded.startsWith(PREFIX))return false;
    byte[] bytes=unhex(encoded.substring(PREFIX.length()));
    if(bytes.length!=48)return false;
    return MessageDigest.isEqual(Arrays.copyOfRange(bytes,16,48),derive(password,Arrays.copyOf(bytes,16)));
  }
  public static void main(String[] args) throws Exception {
    if(args.length!=2 || !(args[0].equals("generate")||args[0].equals("verify")))throw new IllegalArgumentException("generate|verify path/to/05_users.sql");
    Path file=Paths.get(args[1]);
    if(args[0].equals("generate")) {
      StringBuilder sql=new StringBuilder("-- PUBLIC DEVELOPMENT ACCOUNTS ONLY. Do not deploy these known passwords to production.\n-- Salt(16 bytes) + PBKDF2-HMAC-SHA256(310000 iterations, 256 bits), hex; Spring Security v5_8 format.\n-- No plaintext passwords are stored in APP_USER. See README for the public fixture logins.\n");
      SecureRandom random=new SecureRandom();
      for(String[] user:USERS){
        byte[] salt=new byte[16];random.nextBytes(salt);
        String encoded=PREFIX+hex(salt)+hex(derive(user[2],salt));
        sql.append("INSERT INTO APP_USER (USER_ID,EMAIL,PASSWORD,USER_NAME,ROLE_CODE) VALUES (").append(user[0]).append(",'").append(user[1]).append("','").append(encoded).append("','").append(user[3]).append("','").append(user[4]).append("');\n");
      }
      Files.write(file,sql.toString().getBytes(StandardCharsets.UTF_8));
      System.out.println("Generated 3 public development account hashes.");
    } else {
      String sql=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
      Set<String> salts=new HashSet<String>();
      for(String[] user:USERS){
        Matcher matcher=Pattern.compile("'"+Pattern.quote(user[1])+"','([^']+)'").matcher(sql);
        if(!matcher.find())throw new AssertionError("Missing fixture "+user[1]);
        String encoded=matcher.group(1);
        if(!matches(user[2],encoded)||matches("incorrect-password",encoded))throw new AssertionError("Password verification failed");
        if(!salts.add(encoded.substring(PREFIX.length(),PREFIX.length()+32)))throw new AssertionError("Repeated salt");
      }
      System.out.println("PASS: all 3 seeded hashes match fixture passwords, reject wrong passwords, and use distinct salts.");
    }
  }
}
