package com.metafour.trackingmcp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TrackingService {

    private static final Pattern SYSTEM_RE = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");
    private static final Pattern TRACKING_RE = Pattern.compile("^[A-Za-z0-9_-]{4,64}$");
    // UK-style postcodes and similar: letters/digits/space/hyphen only, sane length
    private static final Pattern POSTCODE_RE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 -]{1,15}$");

    private static final String TRACKING_PATH = "/online/tracking/trackinginfo/";
    private static final String VALIDATE_PATH = "/online/tracking/trackinginfo/validate/";

    private final TrackingProperties props;
    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public TrackingService(TrackingProperties props) {
        this.props = props;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.timeout().toMillis());
        factory.setReadTimeout((int) props.timeout().toMillis());
        this.client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "tracking-mcp-server/1.0")
                .defaultHeader("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                .build();
    }

    // ---------- Tracking info ----------

    public String buildUrl(String system, String trackingNo) {
        String sys = normalizeSystem(system);
        if (!SYSTEM_RE.matcher(sys).matches()) {
            throw new IllegalArgumentException("Invalid system name: '" + system + "'");
        }
        if (trackingNo == null || !TRACKING_RE.matcher(trackingNo.trim()).matches()) {
            throw new IllegalArgumentException("Invalid tracking number: '" + trackingNo + "'");
        }
        return baseUrl(sys) + TRACKING_PATH + UriUtils.encodePathSegment(trackingNo.trim(), StandardCharsets.UTF_8);
    }

    public String fetchTracking(String system, String trackingNo) {
        String url = buildUrl(system, trackingNo);
        String body = client.get().uri(URI.create(url)).retrieve().body(String.class);
        String text = bodyToText(body);
        if (text.isBlank()) {
            return "The tracking page (" + url + ") returned no readable content.";
        }
        return "Source: " + url + "\n\n" + truncate(text);
    }

    // ---------- Proof of delivery: location + signature ----------

    public String buildValidateUrl(String system, String trackingNo, String postcode) {
        String sys = normalizeSystem(system);
        /*
        if (!SYSTEM_RE.matcher(sys).matches()) {
            throw new IllegalArgumentException("Invalid system name: '" + system + "'");
        }
        if (trackingNo == null || !TRACKING_RE.matcher(trackingNo.trim()).matches()) {
            throw new IllegalArgumentException("Invalid tracking number: '" + trackingNo + "'");
        }
        if (postcode == null || !POSTCODE_RE.matcher(postcode.trim()).matches()) {
            throw new IllegalArgumentException("Invalid postcode: '" + postcode + "'");
        }
        */
        return baseUrl(sys) + VALIDATE_PATH
                + UriUtils.encodePathSegment(trackingNo.trim(), StandardCharsets.UTF_8) + "/"
                + UriUtils.encodePathSegment(postcode.trim(), StandardCharsets.UTF_8);
    }

    public String fetchDeliveryProof(String system, String trackingNo, String postcode) {
        String url = buildValidateUrl(system, trackingNo, postcode);
        String body = client.get().uri(URI.create(url)).retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            return "The delivery-proof page (" + url + ") returned no content.";
        }

        String trimmed = body.trim();
        StringBuilder out = new StringBuilder("Source: ").append(url).append("\n\n");

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // JSON response: pull out anything that looks like location/signature fields
            try {
                JsonNode root = mapper.readTree(trimmed);
                Map<String, String> found = new LinkedHashMap<>();
                collectFields(root, "", found);
                if (found.isEmpty()) {
                    out.append("Raw response:\n").append(truncate(trimmed));
                } else {
                    found.forEach((k, v) -> out.append(k).append(": ").append(v).append("\n"));
                }
            } catch (Exception e) {
                out.append("Raw response (could not parse as JSON):\n").append(truncate(trimmed));
            }
        } else {
            // HTML/plain text response
            String text = bodyToText(trimmed);
            out.append(text.isBlank() ? "No readable content." : truncate(text));
        }
        return out.toString();
    }

    /** Recursively walk a JSON tree collecting values whose key mentions location/signature/POD-related terms. */
    private void collectFields(JsonNode node, String path, Map<String, String> found) {
        if (node == null) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String childPath = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
                String keyLower = e.getKey().toLowerCase(Locale.ROOT);
                if (e.getValue().isValueNode()
                        && (keyLower.contains("location") || keyLower.contains("signature")
                            || keyLower.contains("postcode") || keyLower.contains("signedby")
                            || keyLower.contains("signed_by") || keyLower.contains("recipient")
                            || keyLower.contains("deliveredto") || keyLower.contains("latitude")
                            || keyLower.contains("longitude"))) {
                    found.put(childPath, e.getValue().asText());
                }
                collectFields(e.getValue(), childPath, found);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectFields(node.get(i), path + "[" + i + "]", found);
            }
        }
    }

    // ---------- shared helpers ----------

    private String baseUrl(String sys) {
        String base = props.systems().getOrDefault(sys, props.baseUrlPattern().replace("{system}", sys));
        return base.replaceAll("/+$", "");
    }

    static String normalizeSystem(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\b(system|portal|courier)\\b", "")
                .trim();
    }

    private String truncate(String text) {
        int max = props.maxChars();
        return text.length() > max ? text.substring(0, max) + "\n…[truncated]" : text;
    }

    private String bodyToText(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed; // let the caller/LLM read raw JSON directly
        }
        return htmlToText(body);
    }

    private String htmlToText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script,style,noscript,svg,head").remove();
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));

        Element root = doc.body();
        String sel = props.contentSelector();
        if (sel != null && !sel.isBlank()) {
            Element e = doc.selectFirst(sel);
            if (e != null) root = e;
        }
        root.select("br").after("\\n");
        root.select("p,div,tr,li,h1,h2,h3,h4,h5,h6,table").before("\\n");
        root.select("td,th").after(" | ");

        return root.text().replace("\\n", "\n")
                .replaceAll("[ \\t]*\\| *(?=\\n|$)", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}