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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.concurrent.Semaphore;

public class ClientThread extends Thread {

    Socket s;
    Handler myHandler;
    Semaphore semaphore;

    public static final String cgi_string = "cgi-bin";

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

            boolean isCGI = false;
            String cgi_command = "";

            while(!tmp.isEmpty()){
                //cgi get command (decode command)
                String req[] = tmp.split(" ");
                if(req[1].contains(cgi_string)){
                    String request = req[1];
                    isCGI = true;

                    int startIndex = request.indexOf(cgi_string)+cgi_string.length()+1; //start index of command (need to remove cgi string and /)
                    int endIndex = request.indexOf(" ", startIndex);
                    if (endIndex == -1) {
                        endIndex = request.length();
                    }
                    String command = request.substring(startIndex, endIndex);

                    StringBuilder command_b = new StringBuilder();
                    command_b.append(URLDecoder.decode(command, "UTF-8"));

                    cgi_command = command_b.toString();
                }

                if(tmp.startsWith("User")){
                    user = tmp;
                }

                if (tmp.startsWith("GET")) {
                    //Log.d("get_tmp",tmp);
                    String parts[] = tmp.split(" ");
                    if (parts.length > 2) {
                        filename = parts[1];
                    }
                }
                if (filename.equals("/")) filename = "/index.html";

                Log.d("SERVER", "HTTP REQ" + tmp);
                tmp = in.readLine();
            }
            sendMessageToServer("user"+user);

            String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            //Log.d("SERVER", path);

            if(isCGI){ //cgi
                if(cgi_command.length() > 0) {
                    StringBuilder cgi_sb = new StringBuilder();

                    //https://www.developer.com/java/data/understanding-java-process-and-java-processbuilder.html?fbclid=IwAR1-Dmw0AxvkcipOq-sept-IZo3o2rq1XhBPf07UGtfgfahtkLzwqrtFLgI
                    try {
                        File dir = new File(path + "/CGI");
                        if (! dir.exists()){
                            if (! dir.mkdirs()){
                                Log.d("cgi", "error creating directory");
                                return;
                            }
                        }


                        ProcessBuilder pb;
                        if(cgi_command.contains("cat")){
                            pb = new ProcessBuilder("cat", cgi_command.split(" ")[1]);
                        } else {
                            pb = new ProcessBuilder(cgi_command);
                        }
                        final Process p = pb.start();

                        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        BufferedWriter bw = new BufferedWriter(
                                new FileWriter(new File(path + "/CGI/cgi_out.txt")));

                        String line;
                        while ((line = br.readLine()) != null) {
                            bw.write(line);
                            cgi_sb.append(line + "\n");
                        }
                        bw.close();


                        if(cgi_sb.length() > 0) {
                            Log.d("cgi", cgi_sb.toString());

                            out.write("HTTP/1.0 200 OK\n");
                            out.write("CGI OUTPUT:\n\n");
                            out.write(cgi_sb.toString());
                            out.flush();
                        } else {
                            Log.d("cgi", "Not valid command");
                            out.write("HTTP/1.0 404 Not Found\n\n");
                            out.write("CGI OUTPUT ERROR:\n\n");
                            out.flush();
                        }

                    } catch (Exception ex) {
                        System.out.println(ex);
                    }
                }
            }
            else { //files
                try {
                    byte[] buffer;
                    Log.d("filename", filename);
                    if (filename.contains("/camera/snapshot")) {
                        buffer = MainActivity.getPictureBytes();
                        if (buffer != null) {
                            out.write("HTTP/1.0 200 OK\n");
                            out.write("Content-Type: image/jpeg\n");
                            //out.write("Content-Length: " + buffer.length + "\n");
                            out.write("\n");
                            out.flush();

                            o.write(buffer);
                        }
                    }
                /*else if(filename.contains("/camera/stream")){ //video nefunguje
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
                }*/
                    else {
                        Log.d("filename", path + filename);
                        File f = new File(path + filename);

                        FileInputStream fileIS = new FileInputStream(f);

                        out.write("HTTP/1.0 200 OK\n");

                        if ((filename.endsWith(".htm") || filename.endsWith(".html")))
                            out.write("Content-Type: text/html\n");
                        if ((filename.endsWith(".jpg") || filename.endsWith(".jpeg")))
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
                        Log.d("filename", "success");
                    }

                    s.close();
                    Log.d("SERVER", "Socket Closed");
                } catch (FileNotFoundException e) {
                    Log.d("SERVER_ERROR", e.getMessage());
                    //Log.d("SERVER", "HTTP/1.0 404 Not Found");
                    out.write("HTTP/1.0 404 Not Found\n\n");
                    out.write("Page not found");
                    out.flush();

                }
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
