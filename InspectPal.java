import java.lang.reflect.*;
public class InspectPal {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("com.softwareag.naturalone.natural.paltransactions.external.IPalTransactions");
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().contains("download") || m.getName().contains("read") || m.getName().contains("source") || m.getName().contains("Source")) {
                System.out.println(m.toGenericString());
            }
        }
    }
}
