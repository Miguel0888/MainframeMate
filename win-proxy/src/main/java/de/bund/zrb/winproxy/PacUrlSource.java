package de.bund.zrb.winproxy;

/**
 * Defines how the PAC (Proxy Auto-Config) URL is obtained.
 * <p>
 * The PAC file itself is always evaluated via GraalJS — this enum only controls
 * <em>how</em> the URL to that PAC file is discovered.
 *
 * <pre>{@code
 * // User provides the PAC URL directly:
 * ProxyResult r = WindowsProxyResolver.resolve(target, PacUrlSource.DIRECT,
 *                     "http://wpad.corp.local/wpad.dat");
 *
 * // PAC URL discovered from Windows Registry (all four hives):
 * ProxyResult r = WindowsProxyResolver.resolve(target, PacUrlSource.REGISTRY, null);
 *
 * // PAC URL obtained by running a PowerShell command:
 * ProxyResult r = WindowsProxyResolver.resolve(target, PacUrlSource.POWERSHELL,
 *                     WindowsProxyResolver.DEFAULT_PAC_DISCOVERY_SCRIPT);
 * }</pre>
 *
 * @see WindowsProxyResolver#resolve(String, PacUrlSource, String)
 */
public enum PacUrlSource {

    /**
     * The PAC URL is provided directly by the caller.
     * The {@code pacUrlOrScript} parameter is the full HTTP URL to the PAC file.
     */
    DIRECT,

    /**
     * The PAC URL is discovered from the Windows Registry.
     * Searches all four registry hives (GPO first, then user, then machine)
     * for {@code AutoConfigURL}. Falls back to static proxy settings and
     * WPAD if no PAC URL is found.
     * <p>
     * The {@code pacUrlOrScript} parameter is ignored (may be {@code null}).
     */
    REGISTRY,

    /**
     * The PAC URL is obtained by running a PowerShell command.
     * The {@code pacUrlOrScript} parameter contains the PowerShell command
     * whose stdout output is the PAC URL.
     * <p>
     * If {@code null} or empty, the {@link WindowsProxyResolver#DEFAULT_PAC_DISCOVERY_SCRIPT}
     * is used as fallback.
     *
     * @see WindowsProxyResolver#DEFAULT_PAC_DISCOVERY_SCRIPT
     */
    POWERSHELL
}
