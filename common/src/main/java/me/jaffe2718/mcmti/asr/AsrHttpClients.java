package me.jaffe2718.mcmti.asr;

import me.jaffe2718.mcmti.MicrophoneTextInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds {@link HttpClient} instances for online ASR, with optional HTTP proxy.
 * <p>
 * Proxy formats (empty = direct):
 * <ul>
 *   <li>{@code host:port}</li>
 *   <li>{@code http://host:port}</li>
 *   <li>{@code http://user:pass@host:port}</li>
 * </ul>
 */
public final class AsrHttpClients {
    private AsrHttpClients() {}

    public static @NotNull HttpClient create(int timeoutMs, @Nullable String proxySpec) {
        int connectMs = Math.max(1_000, timeoutMs);
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .followRedirects(HttpClient.Redirect.NORMAL);

        ParsedProxy proxy = parseProxy(proxySpec);
        if (proxy != null) {
            builder.proxy(ProxySelector.of(proxy.address()));
            if (proxy.username() != null) {
                char[] password = proxy.password() == null
                        ? new char[0]
                        : proxy.password().toCharArray();
                String username = proxy.username();
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == RequestorType.PROXY) {
                            return new PasswordAuthentication(username, password);
                        }
                        return null;
                    }
                });
            }
            MicrophoneTextInput.LOGGER.info(
                    "ASR HTTP proxy enabled: {}:{}{}",
                    proxy.address().getHostString(),
                    proxy.address().getPort(),
                    proxy.username() != null ? " (auth)" : ""
            );
        }

        return builder.build();
    }

    /**
     * @return parsed proxy, or null if blank / direct
     * @throws IllegalArgumentException if the spec is non-blank but invalid
     */
    static @Nullable ParsedProxy parseProxy(@Nullable String proxySpec) {
        if (proxySpec == null) {
            return null;
        }
        String raw = proxySpec.trim();
        if (raw.isEmpty()) {
            return null;
        }

        try {
            if (raw.contains("://")) {
                return parseUriProxy(raw);
            }
            return parseHostPort(raw, null, null);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid HTTP proxy: " + raw, e);
        }
    }

    private static @NotNull ParsedProxy parseUriProxy(@NotNull String raw) {
        URI uri = URI.create(raw);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException(
                    "HTTP proxy must use http:// (or host:port), got scheme: " + uri.getScheme()
            );
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("HTTP proxy missing host: " + raw);
        }
        int port = uri.getPort();
        if (port <= 0) {
            port = scheme.equals("https") ? 443 : 80;
        }
        String user = null;
        String pass = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            if (colon < 0) {
                user = userInfo;
                pass = "";
            } else {
                user = userInfo.substring(0, colon);
                pass = userInfo.substring(colon + 1);
            }
        }
        return new ParsedProxy(new InetSocketAddress(host, port), user, pass);
    }

    private static @NotNull ParsedProxy parseHostPort(
            @NotNull String raw,
            @Nullable String username,
            @Nullable String password
    ) {
        String host;
        int port;
        if (raw.startsWith("[")) {
            // [IPv6]:port
            int close = raw.indexOf(']');
            if (close < 0 || close + 1 >= raw.length() || raw.charAt(close + 1) != ':') {
                throw new IllegalArgumentException("Invalid IPv6 proxy address: " + raw);
            }
            host = raw.substring(1, close);
            port = Integer.parseInt(raw.substring(close + 2));
        } else {
            int colon = raw.lastIndexOf(':');
            if (colon <= 0 || colon == raw.length() - 1) {
                throw new IllegalArgumentException(
                        "HTTP proxy must be host:port or http://host:port, got: " + raw
                );
            }
            host = raw.substring(0, colon);
            port = Integer.parseInt(raw.substring(colon + 1));
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("HTTP proxy missing host: " + raw);
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("HTTP proxy port out of range: " + port);
        }
        return new ParsedProxy(new InetSocketAddress(host, port), username, password);
    }

    record ParsedProxy(
            @NotNull InetSocketAddress address,
            @Nullable String username,
            @Nullable String password
    ) {}
}
