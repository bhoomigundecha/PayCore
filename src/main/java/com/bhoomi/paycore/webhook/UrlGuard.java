package com.bhoomi.paycore.webhook;

import com.bhoomi.paycore.common.InvalidRequestException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SSRF protection. A webhook URL is chosen by the merchant, and PayCore's servers call it. Without this
 * check a merchant could point it at internal addresses (the database, Redis, the cloud metadata service
 * at 169.254.169.254) and use PayCore to reach them.
 *
 * <p>Checked when the endpoint is registered and again before every delivery, because DNS answers can
 * change. Redirects are never followed.
 */
@Component
public class UrlGuard {

    private final boolean allowPrivate;

    public UrlGuard(@Value("${paycore.webhooks.allow-private:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    public URI check(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Webhook URL is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !(allowPrivate && scheme.equals("http"))) {
            throw new InvalidRequestException("Webhook URL must use https");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new InvalidRequestException("Webhook URL must have a host and no user:password part");
        }
        if (allowPrivate) {
            return uri;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new InvalidRequestException("Webhook host does not resolve: " + uri.getHost());
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new InvalidRequestException("Webhook URL points to a private or internal address");
            }
        }
        return uri;
    }

    static boolean isInternal(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true; // 127/8, 0.0.0.0, 169.254/16 (cloud metadata), 10/8, 172.16/12, 192.168/16, ...
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0                                   // "this network"
                    || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT 100.64/10
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0) // IETF protocol assignments
                    || first >= 240;                            // reserved, broadcast
        }
        if (a instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) {
                return true; // unique local fc00::/7
            }
            // IPv4-mapped (::ffff:a.b.c.d): judge the embedded IPv4 address.
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isInternal(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }
}
