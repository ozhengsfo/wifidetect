package com.scrm.test.wifidetect;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    Messenger mServiceMessenger;
    Messenger mActivityMessenger;
    private int mCount = 0;
    private wifiDetectService.wifiStatus mWifiStatus;
    private lteDetectService.LteStatus mLteStatus;
    public static final String TAG = "WIFI_PROTECT";
    public Button StartWifiTest;
    public Button btnStartLteTest;
    private Button btnAutoReConnect;
    private TextView tvCommunicateStatus;
    public static Context mContext;
    public static boolean wifiRunning = false;
    public static boolean lteRunning = false;
    public static boolean mAutoReconect = false; //默认是关闭的
    public TextView mConnectTime;
    public TextView mEditContectTime;
    //是否启动自动重连
    private final int MSG_START_SERVICE = 0;
    private final int MSG_SET_AUTO_RECONNECT = 1;
    private final int MSG_ENABLE_AUTO_RECONNECT = 1;
    private final int MSG_DISABLE_AUTO_RECONNECT = 0;
  //  EditText editResult;

    public TextView mReConnectTimes;
    public TextView PowerOnTime;
    public TextView mRunningTimes;
    public TextView mHttpFailTimes;
    Intent intent;
    private boolean isMessengerServiceConnected = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mConnectTime=(TextView)findViewById(R.id.wifi_connect_times);
        mEditContectTime = (TextView) findViewById(R.id.ewifi_connect_time);
        mReConnectTimes  = (TextView) findViewById(R.id.reconnect_times);
        PowerOnTime = (TextView) findViewById(R.id.poweron_times);
        tvCommunicateStatus = (TextView) findViewById(R.id.CommunicateStatus);
        mRunningTimes = (TextView) findViewById(R.id.running_times);
        mHttpFailTimes  = (TextView) findViewById(R.id.http_fail_times);
        StartWifiTest = findViewById(R.id.StartTest);
        btnStartLteTest = findViewById(R.id.btnLteTest);
        btnAutoReConnect = findViewById(R.id.AutoReConnect);
      //  editResult = findViewById(R.id.editText);
      //  editResult.setText("ssssssssssssssssssssddddddddddddddddddddaaaaaaaaaaaaaaaaaaaaaaa");
        mActivityMessenger = new Messenger(mMessengerHandler);
        StartWifiTest.setOnClickListener(this);
        btnStartLteTest.setOnClickListener(this);
        btnAutoReConnect.setOnClickListener(this);
        String name = "test";

    }

    /**
     * 刷新数字
     */
    private void updateCount() {
        //由于从binder调用回来是在子线程里，需要post到主线程调用
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mEditContectTime.setText(Integer.toString(mCount));
            }
        });
    }

    private void updateNetStatusView(){
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if(wifiRunning == true) {
                    mReConnectTimes.setText(Integer.toString(mCount));
                    PowerOnTime.setText(mWifiStatus.powerOnTime);
                    mEditContectTime.setText(mWifiStatus.wifiLastTime);
                    mRunningTimes.setText(mWifiStatus.ServiceRunTime);
                    mHttpFailTimes.setText(String.valueOf(mWifiStatus.HttpFailTimes));
                    if(mWifiStatus.CommunicateStatus == true){
                        tvCommunicateStatus.setText("已连接");
                        tvCommunicateStatus.setTextColor(getResources().getColor(R.color.main_blue));
                        GradientDrawable drawable = new GradientDrawable();
                        drawable.setCornerRadius(3);
                        drawable.setStroke(1, Color.parseColor("#505050"));
                        drawable.setColor(getResources().getColor(R.color.txt_green));
                        tvCommunicateStatus.setBackground(drawable);
                        //tvCommunicateStatus.stroke
                    }else {
                        tvCommunicateStatus.setText("连接失败");
                        tvCommunicateStatus.setTextColor(getResources().getColor(R.color.txt_red));
                        tvCommunicateStatus.setBackground(getResources().getDrawable(R.drawable.edit_bg));
                    }
                }else if(lteRunning ==true){
                    mReConnectTimes.setText(Integer.toString(mCount));
                    PowerOnTime.setText(mLteStatus.powerOnTime);
                    mEditContectTime.setText(mLteStatus.wifiLastTime);
                }
            }
        });
    }

    private void stopAll() {
        if (isMessengerServiceConnected) {
            unbindService(messengerServiceConnection);
            isMessengerServiceConnected = false;
        }
    }

    static final int WIFI = 1;
    static final  int LTE = 2;
    private void startMessengerMethod(int tag) {
        if(tag == WIFI) {
            intent = new Intent(this, wifiDetectService.class);
            startService(intent);
            intent.putExtra("model", tag);
            bindService(intent, messengerServiceConnection, Service.BIND_AUTO_CREATE);
        }else if (tag == LTE){
            intent = new Intent(this, lteDetectService.class);
            intent.putExtra("model", tag);
            bindService(intent, messengerServiceConnection, Service.BIND_AUTO_CREATE);
        }
    }
    public boolean isMobileConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mMobileNetworkInfo = mConnectivityManager
                    .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (mMobileNetworkInfo != null) {
                //return mMobileNetworkInfo.isAvailable();
                return mMobileNetworkInfo.isConnected();
            }
        }
        return false;
    }
    public boolean isWifiConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mWiFiNetworkInfo = mConnectivityManager
                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (mWiFiNetworkInfo != null) {
                //return mWiFiNetworkInfo.isAvailable();
                return mWiFiNetworkInfo.isConnected();
            }
        }
        return false;
    }
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.StartTest:
                if(isWifiConnected(this) == false){
                    Toast toast = Toast.makeText(this,"请连接WIFI网络",Toast.LENGTH_LONG);
                    toast.show();
                    break;
                }
                if(lteRunning == false) {
                    if (wifiRunning == false) {
                        StartWifiTest.setText("停止");
                        startMessengerMethod(WIFI);
                        wifiRunning = true;
                    } else {
                        StartWifiTest.setText("启动WIFI监测");
                        wifiRunning = false;
                        stopAll();
                    }
                }
                break;
            case R.id.btnLteTest:
                if(isMobileConnected(this) == false){
                    Toast toast = Toast.makeText(this,"请先连接4G网络",Toast.LENGTH_SHORT);
                    toast.show();
                    break;
                }
                if(wifiRunning == false) {
                    if (lteRunning == false) {
                        btnStartLteTest.setText("停止");
                        // StartWifiTest.setBackground(new ColorDrawable(0x0000FF));
                        startMessengerMethod(LTE);
                        lteRunning = true;
                    } else {
                        btnStartLteTest.setText("启动LTE监测");
                        // StartWifiTest.setBackground(new ColorDrawable(0x555555));
                        lteRunning = false;
                        stopAll();
                    }
                }
                break;
            case R.id.AutoReConnect:
                if(isMessengerServiceConnected == true){
                    if(mServiceMessenger != null){

                        Message message = Message.obtain();
                        message.what = MSG_SET_AUTO_RECONNECT;
                        if(mAutoReconect == true){
                            message.arg1 = MSG_DISABLE_AUTO_RECONNECT;
                            btnAutoReConnect.setText("开启自动重连");
                            mAutoReconect = false;
                        }else {
                            message.arg1 = MSG_ENABLE_AUTO_RECONNECT;
                            btnAutoReConnect.setText("关闭自动重连");
                            mAutoReconect = true;
                        }
                        try {
                            Log.d(TAG,"close auto reconnect");
                            mServiceMessenger.send(message);
                        }catch (RemoteException e){
                            e.printStackTrace();
                        }
                    }
                }
                break;
           default:
                break;
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            startActivity(home);
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }
    //messenger使用
    private ServiceConnection messengerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isMessengerServiceConnected = true;

            mServiceMessenger = new Messenger(service);

            Message message = Message.obtain();
            message.replyTo = mActivityMessenger;
            message.what = MSG_START_SERVICE;
            try {
                mServiceMessenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private Handler mMessengerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == WIFI){
                mCount = msg.arg1;
                mWifiStatus = (wifiDetectService.wifiStatus)msg.obj;
                updateNetStatusView();
            }
            else if(msg.what == LTE){
                mCount = msg.arg1;
                mLteStatus = (lteDetectService.LteStatus)msg.obj;
                updateNetStatusView();
            }

            super.handleMessage(msg);
        }
    };

}
