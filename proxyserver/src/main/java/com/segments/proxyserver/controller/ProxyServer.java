package com.segments.proxyserver.controller;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ProxyServer {
    public static void serveStart() throws IOException {

        ServerSocket serverSocket = new ServerSocket(8000);

        System.out.println("Proxy server started on port 8000");

        while(true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Accepted connection from " + clientSocket.getInetAddress().getHostName());

            new Thread(() -> handleClientRequest(clientSocket)).start();
        }

    }

    private static void handleClientRequest(Socket clientSocket) {
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            while(true) {

                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line = reader.readLine();
                System.out.println("Received: " + line);

                out.write(line.getBytes());
                out.flush();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
