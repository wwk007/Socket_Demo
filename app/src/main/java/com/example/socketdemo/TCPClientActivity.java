package com.example.socketdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TCPClientActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "TCPClientActivity";
    private static final int MESSAGE_RECEIVE_NEW_MSG = 1;
    private static final int MESSAGE_SOCKET_CONNECTED = 2;

    private Button mSendBtn;
    private TextView mShowTV;
    private EditText mInput;

    private Socket mClientSocket;
    private PrintWriter mPrintWriter;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(){
        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_RECEIVE_NEW_MSG:
                    Log.d(TAG, "MESSAGE_RECEIVE_NEW_MSG");
                    mShowTV.setText(mShowTV.getText() + (String) msg.obj);
                    break;
                case MESSAGE_SOCKET_CONNECTED:
                    Log.d(TAG, "MESSAGE_SOCKET_CONNECTED");
                    mSendBtn.setEnabled(true);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSendBtn = (Button)findViewById(R.id.send);
        mShowTV = (TextView)findViewById(R.id.msg);
        mInput = (EditText)findViewById(R.id.edit);

        mSendBtn.setOnClickListener(this);

        Intent intent = new Intent(this, TCPServerService.class);
        startService(intent);

        new Thread() {
            @Override
            public void run() {
                super.run();
                connectTCPServer();
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        if(mClientSocket != null) {
            try {
                mClientSocket.shutdownInput();
                mClientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onClick(View view) {
        if(view == mSendBtn) {
            final String msg = mInput.getText().toString();
            if(!TextUtils.isEmpty(msg) && mPrintWriter != null) {
                new Thread(){
                    @Override
                    public void run() {
                        super.run();
                        mPrintWriter.println(msg);
                    }
                }.start();

                mInput.setText("");
                String time = formatDateTime(System.currentTimeMillis());
                final String showedMsg = "self " + time + ":" + msg +"\n";
                mShowTV.setText(mShowTV.getText() + showedMsg);
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private String formatDateTime(long time) {
        return new SimpleDateFormat("(HH:mm:ss)").format(new Date(time));
    }

    private void connectTCPServer() {
        Socket socket = null;
        while (socket == null) {
            try {
                //等5s, 让服务端先启动起来，不然会出现IOException
                //IOException e:failed to connect to localhost/127.0.0.1 (port 8688) from /:: (port 40640): connect failed: ECONNREFUSED (Connection refused)
                Thread.sleep(5000);
                Log.d(TAG, "socket:"+InetAddress.getLocalHost());
                socket = new Socket(InetAddress.getLocalHost(), 8688);
                mClientSocket = socket;
                mPrintWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                mHandler.sendEmptyMessage(MESSAGE_SOCKET_CONNECTED);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                Log.d(TAG, "IOException e:"+e.getMessage());
                return;
            }

            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (!TCPClientActivity.this.isFinishing()) {
                    String msg = br.readLine();
                    if(msg != null) {
                        String time = formatDateTime(System.currentTimeMillis());
                        final String showedMsg = "server " + time + ":" + msg +"\n";
                        mHandler.obtainMessage(MESSAGE_RECEIVE_NEW_MSG, showedMsg).sendToTarget();
                    }
                }
                mPrintWriter.close();
                br.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
