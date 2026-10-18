package com.wisprfox.android.sync

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * DNS-over-HTTPS resolver for Supabase hosts (work item: "Supabase DNS block").
 *
 * Several Indian ISPs DNS-poison `*.supabase.co`: the recursive resolver the
 * phone is handed by the network returns a sinkhole/parking IP instead of
 * Supabase's real address, so the TLS handshake to that wrong host fails with
 * an EOF and every sign-in / sync / cloud-delete call dies before it starts.
 * The data path is fine — only name resolution is tampered with — so the fix is
 * to resolve the Supabase hostname ourselves over an encrypted DoH channel the
 * ISP cannot sinkhole, and hand OkHttp the real addresses.
 *
 * This mirrors the desktop fix (resolve Supabase hosts via DoH to Cloudflare
 * then Google). Only hosts ending in `supabase.co` are routed through DoH;
 * every other hostname (api.groq.com, generativelanguage.googleapis.com,
 * api.github.com, …) uses the platform resolver unchanged, so this adds no
 * latency or new failure mode to the BYOK provider traffic.
 *
 * Resolution order for a Supabase host:
 *   1. Cloudflare DoH (https://1.1.1.1/dns-query, bootstrap 1.1.1.1 / 1.0.0.1)
 *   2. Google DoH     (https://8.8.8.8/dns-query, bootstrap 8.8.8.8 / 8.8.4.4)
 *   3. [Dns.SYSTEM]    (only if BOTH DoH providers fail — e.g. the DoH
 *                       endpoints themselves are blocked; better a poisoned
 *                       answer than no answer, since on an untampered network
 *                       the system resolver is correct)
 *
 * The DoH endpoints are addressed by IP with the IPs supplied as bootstrap
 * hosts, so resolving the resolver never itself needs DNS. Both Cloudflare's
 * `1.1.1.1`/`1.0.0.1` and Google's `8.8.8.8`/`8.8.4.4` certificates carry those
 * IP literals in their SAN list, so the TLS handshake to the IP URL validates.
 */
object SupabaseDns {

    private const val SUPABASE_SUFFIX = "supabase.co"

    /**
     * Build a [Dns] that routes Supabase hostnames through DoH and everything
     * else through [Dns.SYSTEM].
     *
     * @param bootstrapClient a plain OkHttpClient used only to carry the DoH
     *   lookups. It MUST NOT itself use the returned [Dns] (that would recurse);
     *   pass a client with the default system resolver. The Supabase/provider
     *   clients are built with `newBuilder()` off a base client, so pass that
     *   base client's own `newBuilder().build()` or a fresh `OkHttpClient()`.
     */
    fun create(bootstrapClient: OkHttpClient): Dns {
        val cloudflare = runCatching {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://1.1.1.1/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                )
                .build()
        }.getOrNull()

        val google = runCatching {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://8.8.8.8/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("8.8.4.4"),
                )
                .build()
        }.getOrNull()

        // OkHttp 4's Dns is a plain (non-fun) Kotlin interface, so no SAM lambda.
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                if (!hostname.endsWith(SUPABASE_SUFFIX)) {
                    return Dns.SYSTEM.lookup(hostname)
                }
                return resolveViaDoh(cloudflare, hostname)
                    ?: resolveViaDoh(google, hostname)
                    ?: Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    /** One DoH lookup, swallowing failures so the caller can fall through. */
    private fun resolveViaDoh(doh: Dns?, hostname: String): List<InetAddress>? {
        if (doh == null) return null
        return runCatching { doh.lookup(hostname) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }
}
