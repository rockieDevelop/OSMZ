package com.vsb.kru13.osmzhttpserver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
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
import java.util.concurrent.Semaphore;

public class SocketServer extends Thread {

    private Handler myHandler;

    private boolean cameraExists;

    ServerSocket serverSocket;
    public final int port = 12345;
    boolean bRunning;

    private int maxThreads;
    private Semaphore semaphore;

    public SocketServer(Handler handler, int maxThreads){
        myHandler = handler;

        this.maxThreads = maxThreads;
        semaphore = new Semaphore(maxThreads);
    }

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
                //Log.d("SERVER", "Socket Waiting for connection");
                Socket s = serverSocket.accept();
                //Log.d("SERVER", "Socket Accepted");
                boolean accept = semaphore.tryAcquire(1);
                if(accept)
                    new ClientThread(s, myHandler, semaphore).start();
                else
                    Log.d("waiting","waiting to release sources");

            }
        }
        catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
                Log.d("SERVER", "Normal exit");
            else {
                Log.d("SERVER", "Error");
                e.printStackTrace();
            }
        } finally {
            serverSocket = null;
            bRunning = false;
        }
    }

}

