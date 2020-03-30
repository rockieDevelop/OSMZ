package com.vsb.kru13.osmzhttpserver;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class SocketServer extends Thread {

    ServerSocket serverSocket;
    public final int port = 12345;
    boolean bRunning;

    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            Log.d("SERVER", "Error, probably interrupted in accept(), see log");
            e.printStackTrace();
        }
        bRunning = false;
    }

    public void run() {
        try {
            Log.d("SERVER", "Creating Socket");
            serverSocket = new ServerSocket(port);
            bRunning = true;

            while (bRunning) {
                Log.d("SERVER", "Socket Waiting for connection");
                Socket s = serverSocket.accept();
                Log.d("SERVER", "Socket Accepted");

                OutputStream o = s.getOutputStream();
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(o));
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                String tmp = "";
                String filename = "";

                tmp = in.readLine();
                if(tmp == null)
                    tmp = "";
                while(!tmp.isEmpty()){

                    if (tmp.startsWith("GET")) {
                        String parts[] = tmp.split(" ");
                        if (parts.length > 2) {
                            filename = parts[1];
                        }
                    }
                    if (filename.equals("/")) filename = "/index.html";

                    Log.d("SERVER", "HTTP REQ" + tmp);
                    tmp = in.readLine();
                }

                String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                Log.d("SERVER", path);

                try {
                    File f = new File(path + filename);

                    FileInputStream fileIS = new FileInputStream(f);

                    out.write("HTTP/1.0 200 OK\n");

                    if ((filename.endsWith(".htm") || filename.endsWith(".html")))
                        out.write("Content-Type: text/html\n");
                    if ((filename.endsWith(".jpg") || filename.endsWith(".jpeg")))
                        out.write("Content-Type: image/jpeg\n");
                    if ((filename.endsWith(".png"))) out.write("Content-Type: image/png\n");

                    out.write("Content-Length: " + String.valueOf(f.length()) + "\n");
                    out.write("\n");
                    out.flush();

                    int c;
                    byte[] buffer = new byte[1024];

                    while ((c = fileIS.read(buffer)) != -1) {
                        o.write(buffer, 0, c);
                    }

                    fileIS.close();
                    s.close();
                    Log.d("SERVER", "Socket Closed");
                } catch (FileNotFoundException e) {
                    Log.d("SERVER",e.getMessage());
                    //Log.d("SERVER", "HTTP/1.0 404 Not Found");
                    out.write("HTTP/1.0 404 Not Found\n\n");
                    out.write("Page not found");
                    out.flush();
                }


            }
        }
        catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
                Log.d("SERVER", "Normal exit");
            else {
                Log.d("SERVER", "Error");
                e.printStackTrace();
            }
        }
        finally {
            serverSocket = null;
            bRunning = false;
        }
    }

}

