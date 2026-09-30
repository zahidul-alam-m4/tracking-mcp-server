# Tracking MCP Server (Spring Boot + Spring AI 1.1, Streamable HTTP)

Tool: `get_parcel_tracking(system, trackingNo)` -> fetches
`https://<system>.netcourier.net/online/tracking/trackinginfo/<trackingNo>` and returns readable text.

MCP endpoint: `https://<your-domain>/mcp`

## Deploy with HTTPS (recommended: Docker + Caddy, automatic Let's Encrypt)
1. Point a DNS A record (e.g. `mcp.yourcompany.com`) to your server's public IP.
2. Open ports 80 and 443 on the server/firewall.
3. On the server:
       export MCP_DOMAIN=mcp.yourcompany.com
       docker compose up -d --build
4. Verify:
       curl -i https://mcp.yourcompany.com/mcp        # 4xx/405 without a proper MCP request is fine - it proves TLS + routing work
   Or with MCP Inspector: `npx @modelcontextprotocol/inspector` -> Streamable HTTP -> https://mcp.yourcompany.com/mcp

Alternatives: any platform that gives you HTTPS (Azure App Service, AWS ALB/ECS, Cloud Run, Render, Railway...) -
just run the container on port 8080; `forward-headers-strategy: framework` is already set.

## Native TLS in Spring Boot (no proxy)
    keytool -importkeystore ... (or openssl pkcs12 -export) -> keystore.p12  (alias "tracking", must be a CA-signed cert - Claude/ChatGPT reject self-signed)
    java -jar app.jar --spring.profiles.active=ssl   # env: SSL_KEYSTORE, SSL_KEYSTORE_PASSWORD

## Connect the clients
- **claude.ai**: Settings -> Connectors -> Add custom connector -> URL `https://mcp.yourcompany.com/mcp`
  (Team/Enterprise: an org owner adds it first).
- **ChatGPT**: Settings -> Connectors -> Advanced -> Developer mode -> Create connector -> URL `https://mcp.yourcompany.com/mcp`, auth: none.
- **Claude Desktop (local stdio)**: run with `--spring.profiles.active=stdio` and register the jar as a command.

## Security
The tracking data is public, so authless is acceptable, but a public MCP URL can be abused as an HTTP fetcher/DoS target.
Built-in protection: only `/online/tracking/trackinginfo/` on `*.netcourier.net` (or configured systems) can be fetched, strict input validation.
- Set `TRACKING_SECURITY_API_KEY` to require `Authorization: Bearer <key>` / `X-API-Key` (works for API/SDK/Inspector clients;
  the claude.ai and ChatGPT connector UIs generally only offer none/OAuth, so use OAuth or network controls there).
- Add rate limiting at Caddy/WAF level.
