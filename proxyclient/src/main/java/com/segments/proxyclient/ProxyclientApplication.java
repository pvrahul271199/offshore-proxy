package com.segments.proxyclient;

import com.segments.proxyclient.tunnel.TunnelClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;



public class ProxyclientApplication {

    private static final int LOCAL_PORT    = 8080;
    private static final String OFFSHORE_HOST = System.getenv().getOrDefault("OFFSHORE_HOST", "localhost");
    private static final int OFFSHORE_PORT    = Integer.parseInt(System.getenv().getOrDefault("OFFSHORE_PORT", "9090"));

    // one tunnel connection shared by everyone
    private static TunnelClient tunnel;

    // one lock so requests go one at a time
    private static final Object LOCK = new Object();

    public static void main(String[] args) throws Exception {
        tunnel = new TunnelClient(OFFSHORE_HOST, OFFSHORE_PORT);

        ServerSocket server = new ServerSocket(LOCAL_PORT);
        System.out.println("Ship Proxy listening on port " + LOCAL_PORT);

        while (true) {
            Socket browser = server.accept();
            // each browser connection gets a thread
            // but LOCK ensures only 1 goes through tunnel at a time
            new Thread(() -> handleBrowser(browser)).start();
        }
    }

    private static void handleBrowser(Socket browser) {
        try {
            byte[] request  = readRequest(browser.getInputStream());
            byte[] response;

            // one request at a time
            synchronized (LOCK) {
                tunnel.sendRequest(request);
                response = tunnel.readResponse();
            }

            browser.getOutputStream().write(response);
            browser.getOutputStream().flush();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            try { browser.close(); } catch (Exception ignored) {}
        }
    }

    // Read raw HTTP request from browser
    private static byte[] readRequest(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];

        while (true) {
            int n = in.read(tmp);
            if (n < 0) break;
            buf.write(tmp, 0, n);

            // HTTP headers end with \r\n\r\n
            String text = buf.toString("ISO-8859-1");
            if (text.contains("\r\n\r\n")) break;
        }
        return buf.toByteArray();
    }
}

