import org.eclipse.jetty.security.ConstraintSecurityHandler;
import java.lang.reflect.Method;

public class check_signatures {
    public static void main(String[] args) {
        for (Method m : ConstraintSecurityHandler.class.getDeclaredMethods()) {
            if (m.getName().equals("isAuthMandatory")) {
                System.out.println(m);
            }
        }
        for (Method m : ConstraintSecurityHandler.class.getMethods()) {
            if (m.getName().equals("isAuthMandatory")) {
                System.out.println(m);
            }
        }
    }
}
