package de.bund.zrb.net;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

/**
 * Minimal helper invoked as a <b>subprocess</b> with
 * {@code -Djava.net.useSystemProxies=true} so that Java's
 * {@link ProxySelector} reads the Windows system proxy (WPAD/PAC/WinHTTP)
 * correctly — the property must be set <em>before</em> the default
 * {@code DefaultProxySelector} class is loaded, which only works reliably
 * via a JVM startup argument.
 * <p>
 * <b>Usage:</b>
 * <pre>
 *   java -Djava.net.useSystemProxies=true -cp app.jar de.bund.zrb.net.ProxyProbe https://example.com
 * </pre>
 * <p>
 * <b>Output:</b> a single line — either {@code host:port} or {@code DIRECT}.
 */
public final class ProxyProbe {

    private ProxyProbe() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("DIRECT");
            return;
        }
        try {
            URI uri = new URI(args[0]);
            ProxySelector selector = ProxySelector.getDefault();
            if (selector == null) {
                System.out.println("DIRECT");
                return;
            }
            List<Proxy> proxies = selector.select(uri);
            for (Proxy p : proxies) {
                if (p.type() == Proxy.Type.HTTP || p.type() == Proxy.Type.SOCKS) {
                    InetSocketAddress addr = (InetSocketAddress) p.address();
                    System.out.println(addr.getHostString() + ":" + addr.getPort());
                    return;
                }
            }
            System.out.println("DIRECT");
        } catch (Exception e) {
            System.out.println("DIRECT");
        }
    }
}

