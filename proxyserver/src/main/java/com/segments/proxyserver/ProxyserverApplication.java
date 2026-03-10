package com.segments.proxyserver;

import java.io.*;
import java.net.*;

public class ProxyserverApplication {

    private static final int TUNNEL_PORT =
            Integer.parseInt(System.getenv().getOrDefault("TUNNEL_PORT", "9090"));

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(TUNNEL_PORT);
        System.out.println("Offshore Proxy listening on port " + TUNNEL_PORT);

        while (true) {
            Socket shipSocket = server.accept();
            System.out.println("Ship proxy server connected!");
            // handle in a thread so if ship reconnects we handle it
            new Thread(() -> handleTunnel(shipSocket)).start();
        }
    }

    // Read requests from tunnel, fetch from internet, reply
    private static void handleTunnel(Socket shipSocket) {
        try {
            DataInputStream  in  = new DataInputStream(new BufferedInputStream(shipSocket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(shipSocket.getOutputStream()));

            while (true) {
                // 1. Read request
                int length = in.readInt();
                byte[] requestBytes = new byte[length];
                in.readFully(requestBytes);

                String requestText = new String(requestBytes, "ISO-8859-1");
                System.out.println("Received request:\n" + requestText.split("\r\n")[0]); // print first line only

                // 2. Fetch from internet
                byte[] responseBytes = forwardToInternet(requestText, requestBytes);

                // 3. Send response back
                out.writeInt(responseBytes.length);
                out.write(responseBytes);
                out.flush();
                System.out.println("Response sent (" + responseBytes.length + " bytes)");
            }

        } catch (EOFException e) {
            System.out.println("Ship proxy server disconnected.");
        } catch (Exception e) {
            System.out.println("Tunnel error: " + e.getMessage());
        } finally {
            try { shipSocket.close(); } catch (Exception ignored) {}
        }
    }

    // request to the real internet

    private static byte[] forwardToInternet(String requestText, byte[] rawRequest) {
        try {

            String firstLine = requestText.substring(0, requestText.indexOf("\r\n"));
            String[] parts   = firstLine.split(" ");
            String method    = parts[0];
            String urlStr    = parts[1];

            if (method.equalsIgnoreCase("CONNECT")) {
                return handleConnect(urlStr);
            }

            return handleHttp(method, urlStr, requestText, rawRequest);

        } catch (Exception e) {
            return buildErrorResponse(502, "Bad Gateway: " + e.getMessage());
        }
    }


    private static byte[] handleHttp(String method, String urlStr,
                                     String requestText, byte[] rawRequest) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method);

        // Forward headers from browser (skip proxy-only headers)
        String[] lines = requestText.split("\r\n");
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) break;
            int colon = lines[i].indexOf(':');
            if (colon < 0) continue;
            String name  = lines[i].substring(0, colon).trim();
            String value = lines[i].substring(colon + 1).trim();
            if (name.equalsIgnoreCase("Proxy-Connection")) continue;
            if (name.equalsIgnoreCase("Connection"))       continue;
            conn.setRequestProperty(name, value);
        }

        // Forward request body if present
        int headerEnd = requestText.indexOf("\r\n\r\n");
        if (headerEnd >= 0) {
            byte[] body = new byte[rawRequest.length - (headerEnd + 4)];
            System.arraycopy(rawRequest, headerEnd + 4, body, 0, body.length);
            if (body.length > 0) {
                conn.setDoOutput(true);
                conn.getOutputStream().write(body);
            }
        }

        // response from internet
        return readResponse(conn);
    }


    // read response and build raw bytes

    private static byte[] readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        String statusMsg = conn.getResponseMessage() != null ? conn.getResponseMessage() : "";

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, false, "ISO-8859-1");

        // Status line
        ps.print("HTTP/1.1 " + status + " " + statusMsg + "\r\n");

        // Headers
        for (int i = 0; ; i++) {
            String key = conn.getHeaderFieldKey(i);
            String val = conn.getHeaderField(i);
            if (i == 0 && key == null) { if (val == null) break; continue; }
            if (key == null) break;
            if (key.equalsIgnoreCase("Transfer-Encoding")) continue; // we reassemble body
            ps.print(key + ": " + val + "\r\n");
        }

        // Body
        InputStream bodyStream;
        try {
            bodyStream = conn.getInputStream();
        } catch (IOException e) {
            bodyStream = conn.getErrorStream();
        }
        byte[] body = bodyStream != null ? bodyStream.readAllBytes() : new byte[0];

        ps.print("Content-Length: " + body.length + "\r\n");
        ps.print("\r\n");
        ps.flush();
        buf.write(body);

        return buf.toByteArray();
    }


    private static byte[] handleConnect(String hostPort) throws Exception {
        String[] hp   = hostPort.split(":");
        String host   = hp[0];
        int    port   = hp.length > 1 ? Integer.parseInt(hp[1]) : 443;

        Socket remote = new Socket();
        remote.connect(new InetSocketAddress(host, port), 10_000);
        remote.setSoTimeout(15_000);

        ByteArrayOutputStream response = new ByteArrayOutputStream();

        //  client tunnel is ready
        response.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));

        byte[] buf = new byte[8192];
        int n;
        InputStream remoteIn = remote.getInputStream();
        while ((n = remoteIn.read(buf)) != -1) {
            response.write(buf, 0, n);
        }
        remote.close();
        return response.toByteArray();
    }

    private static byte[] buildErrorResponse(int code, String message) {
        String body = "<html><body><h1>" + code + " " + message + "</h1></body></html>";
        String response = "HTTP/1.1 " + code + " " + message + "\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "\r\n" + body;
        try { return response.getBytes("ISO-8859-1"); } catch (Exception e) { return response.getBytes(); }
    }
}
