package com.vsb.kru13.osmzhttpserver;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
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
import java.net.Socket;
import java.util.concurrent.Semaphore;

public class ClientThread extends Thread {

    Socket s;
    Handler myHandler;
    Semaphore semaphore;

    public ClientThread(Socket socket, Handler handler, Semaphore semaphore){
        s = socket;
        myHandler = handler;
        this.semaphore = semaphore;
    }

    @Override
    public void run() {
        OutputStream o = null;
        try {
            o = s.getOutputStream();
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(o));
            BufferedReader in = null;
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));

            String tmp = "";
            String filename = "";

            tmp = in.readLine();

            if(tmp == null)
            tmp = "";
            String user = "";
            while(!tmp.isEmpty()){

                if(tmp.startsWith("User")){
                    user = tmp;
                }

                if (tmp.startsWith("GET")) {
                    //Log.d("get_tmp",tmp);
                    String parts[] = tmp.split(" ");
                    if (parts.length > 2) {
                        filename = parts[1];
                    }
                    if(tmp.contains("/camera/snapshot")){
                        //return picture from camera
                    }
                }
                if (filename.equals("/")) filename = "/index.html";

                Log.d("SERVER", "HTTP REQ" + tmp);
                tmp = in.readLine();
            }
            sendMessageToServer("user"+user);

            String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            //Log.d("SERVER", path);

            try {
                byte[] buffer;
                Log.d("filename",filename);
                if(filename.contains("/camera/snapshot")){
                    buffer = MainActivity.getPictureBytes();
                    out.write("HTTP/1.0 200 OK\n");
                    out.write("Content-Type: image/jpeg\n");
                    //out.write("Content-Length: " + buffer.length + "\n");
                    out.write("\n");
                    out.flush();

                    o.write(buffer);
                }
                else if(filename.contains("/camera/stream")){
                    out.write("HTTP/1.0 200 OK\n");
                    out.write("Content-Type: multipart/x-mixed-replace; boundary=\"OSMZ_boundary\"\n");

                    for(byte[] bytes : CameraPreview.getPicturesBytes()) {//java.util.ConcurrentModificationException - nejspis to ze s listem pracuje zaroven previewCallback
                        out.write("OSMZ_boundary\n");
                        out.write("Content-Type: image/jpeg\n");
                        //out.write("Content-Length: " + buffer.length + "\n");
                        out.write("\n");
                        out.flush();
                        o.write(bytes);
                    }

                    CameraPreview.clearPicturesBytes();
                }
                else {
                    Log.d("filename", path + filename);
                    File f = new File(path + filename);

                    FileInputStream fileIS = new FileInputStream(f);

                    out.write("HTTP/1.0 200 OK\n");

                    if ((filename.endsWith(".htm") || filename.endsWith(".html")))
                        out.write("Content-Type: text/html\n");
                    if ((filename.endsWith(".jpg") || filename.endsWith(".jpeg") ))
                        out.write("Content-Type: image/jpeg\n");
                    if ((filename.endsWith(".png")))
                        out.write("Content-Type: image/png\n");


                    out.write("Content-Length: " + String.valueOf(f.length()) + "\n");
                    out.write("\n");
                    out.flush();

                    int c;
                    buffer = new byte[1024];

                    while ((c = fileIS.read(buffer)) != -1) {
                        o.write(buffer, 0, c);
                    }
                    fileIS.close();
                    Log.d("filename","success");
                }

                s.close();
                Log.d("SERVER", "Socket Closed");
            } catch (FileNotFoundException e) {
                Log.d("SERVER_ERROR",e.getMessage());
                //Log.d("SERVER", "HTTP/1.0 404 Not Found");
                out.write("HTTP/1.0 404 Not Found\n\n");
                out.write("Page not found");
                out.flush();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            semaphore.release(1);
            Log.d("SERVER", "releasing");
        }
    }

    private void sendMessageToServer(String message){
        Message msg = myHandler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putString("string_key", message);
        msg.setData(bundle);
        myHandler.sendMessage(msg);
    }
}
