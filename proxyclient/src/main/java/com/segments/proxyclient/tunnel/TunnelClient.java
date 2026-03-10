package com.segments.proxyclient.tunnel;

import java.io.*;
import java.net.Socket;

public class TunnelClient {

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    // Connect offshore server
    public TunnelClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setKeepAlive(true);
        this.in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        System.out.println("Connected to offshore: " + host + ":" + port);
    }

    // Send HTTP request bytes
    public void sendRequest(byte[] requestBytes) throws IOException {
        out.writeInt(requestBytes.length);
        out.write(requestBytes);
        out.flush();
    }

    // Read raw HTTP response bytes from offshore
    public byte[] readResponse() throws IOException {
        int length = in.readInt();
        byte[] response = new byte[length];
        in.readFully(response);
        return response;
    }
}








