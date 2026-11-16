// Java MIRROR of the crypto in app/src/main/java/com/nova/assistant/LaptopLink.kt (same JCE calls, same layout).
// It is NOT the Kotlin file itself (no kotlinc in the sandbox); it proves the wire format interoperates with desktop/link.js.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Interop {
    static String b64u(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    static byte[] unb64u(String s) { return Base64.getUrlDecoder().decode(s); }
    static String topic(String key, String dir) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest((dir + ":" + key).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); for (byte x : d) sb.append(String.format("%02x", x));
        return "jarvis-" + sb.substring(0, 40);
    }
    static String seal(String key, String topic, String body) throws Exception {
        byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(unb64u(key), "AES"), new GCMParameterSpec(128, iv));
        c.updateAAD(topic.getBytes(StandardCharsets.UTF_8));
        byte[] ct = c.doFinal(body.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[12 + ct.length]; System.arraycopy(iv, 0, out, 0, 12); System.arraycopy(ct, 0, out, 12, ct.length);
        return b64u(out);
    }
    static String open(String key, String topic, String env) {
        try {
            byte[] raw = unb64u(env); if (raw.length < 29) return "NULL";
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(unb64u(key), "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(raw, 0, 12)));
            c.updateAAD(topic.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(Arrays.copyOfRange(raw, 12, raw.length)), StandardCharsets.UTF_8);
        } catch (Exception e) { return "NULL"; }
    }
    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "topic": System.out.print(topic(a[1], a[2])); break;
            case "seal": System.out.print(seal(a[1], a[2], a[3])); break;
            case "open": System.out.print(open(a[1], a[2], a[3])); break;
        }
    }
}
