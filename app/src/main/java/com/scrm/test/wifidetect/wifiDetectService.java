package com.scrm.test.wifidetect;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class wifiDetectService extends Service {
    public static final String TAG = "WIFI_DETECT";
    private Messenger activityMessenger;
    private MessengerHandler  messengerHandler;
    private int count = 0;
    private static volatile  boolean isRunning;
    public static final  int UPDATE_WIFI = 1;
    public static int WifiThreadTimes = 0;
    private static int OverTimes = 0;//ping 超过一定时间的超时次数
    private static boolean WifiChanged = false;//ping 超过一定时间的超时次数
    private static boolean ENABLE = true;
    private static boolean DISABLE = false;
    private static final int  WIFI_THRESHODE = 3000;// wifi ping 测试时间阈值，大于500表示wifi模块功能异常,连续5次。要重启wifi
    private static int PingFailTimes = 0; // wifi ping 失败次数 连续3次Ping 失败，则重启wifi
    private static boolean PingStatus = false; // ping 执行结果，false 表示ping 失败

    //private static int httpGetFailTimes
    private static int httpGetFailTimes = 0;
    private static int WifiRestartTimes = 0;
    private static long ServiceStartTime = 0; //本次连接wifi 启动时间
    private static long NetLastTime = 0; // wifi持续时间
    private final int MSG_SET_AUTO_RECONNECT = 1;
    private final int MSG_ENABLE_AUTO_RECONNECT = 1;
    private final int MSG_DISABLE_AUTO_RECONNECT = 0;
    public wifiStatus mWifiStatus =  new wifiStatus();
    private Context mContext;
    static final int WIFI = 1;
    static final  int LTE = 2;
    private boolean mAutoReconect = false; //默认关闭
    private static int HttpFailTimes = 0;
    public wifiDetectService(){
        messengerHandler = new MessengerHandler();
    }

    private class MessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == MSG_SET_AUTO_RECONNECT){
                if(msg.arg1 == MSG_ENABLE_AUTO_RECONNECT) {
                    mAutoReconect = true;
                    Log.d(TAG,"enable mAutoReconect");
                }else{
                    Log.d(TAG,"disable mAutoReconect");
                    mAutoReconect = false;
                }
            }else {
                if (msg.replyTo != null) {
                    activityMessenger = msg.replyTo;
                    notifyActivity();
                }
            }
            super.handleMessage(msg);
        }
    }
    public String millsecondToString(long millsecond){
        int totalSeconds = (int) (millsecond / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        //int hour = (totalSeconds/(60*60))%24;
        int hour = (totalSeconds/(60*60));
        String time = hour > 0 ?String.format("%03dh%02dm%02ds",hour,minutes,seconds) :
                String.format("%02dm%02ds",minutes,seconds);
        return time;
    }
    public class wifiStatus{
        String powerOnTime;
        String wifiLastTime;
        String pingResult;
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

    public static int getConnectedType(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null && mNetworkInfo.isConnected()) {
                int type = mNetworkInfo.getSubtype();
                int type2 = mNetworkInfo.getType();
                return mNetworkInfo.getType();

            }
        }
        return -1;
    }

    private void notifyActivity(){
        isRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(isRunning) {
                    httpGetWx();
                    if( isNetSystemUsable(mContext)){
                        Log.d(TAG,"NetSystemUsable is true");
                    }else {
                        Log.d(TAG,"NetSystemUsable is false");
                    }
                    try {
                        if(WifiChanged == false) {
                            isAvailableByHttpGet();
                          //  isAvailableByPing("www.baidu.com");
                            long runTime = SystemClock.elapsedRealtime();
                            String powerOnTime = millsecondToString(runTime);
                            String wifiLastTime = millsecondToString(NetLastTime);
                            mWifiStatus.powerOnTime = powerOnTime;
                            mWifiStatus.wifiLastTime = wifiLastTime;
                        }else {
                            setWifiStatus(ENABLE);
                            WifiRestartTimes ++;
                            WifiChanged = false;
                            Thread.sleep(30000);
                        }
                        //Thread.sleep(1000);
                        Thread.sleep(20000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                   // count++;
                    Message message = Message.obtain();
                    message.what = WIFI;
                    message.arg1 = WifiRestartTimes;
                    message.obj = mWifiStatus;

                    try {
                        activityMessenger.send(message);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mContext = this;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG,"wifi detect service  start");
        mContext = this;
        HttpFailTimes = 0;
        int model = intent.getIntExtra("model",1);
        if(model == WIFI){
            ServiceStartTime = System.currentTimeMillis();
        }
        else if (model == LTE){
            ServiceStartTime = System.currentTimeMillis();
        }

        return new Messenger(messengerHandler).getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG,"wifi detect service  onUnbind");
        isRunning = false;
        return super.onUnbind(intent);
    }

    public boolean getWifiStatus(){
        WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();//){
    }

    public void setWifiStatus(boolean enable){
        WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(enable);
    }
    public static boolean isNetSystemUsable(Context context){
        boolean isNetUsable = false;
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            NetworkCapabilities networkCapabilities =
                    manager.getNetworkCapabilities(manager.getActiveNetwork());
            /* if(networkCapabilities != null) {
                isNetUsable = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }*/
            isNetUsable = (networkCapabilities == null ? false : networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        }
        return isNetUsable;
    }
    private static OkHttpClient sClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    public static boolean httpGetWx() {
        //1,创建okHttpClient对象
        //OkHttpClient mOkHttpClient = new OkHttpClient();
//2,创建一个Request
        final Request request = new Request.Builder()
                .url("https://wx.qq.com")
                .build();
//3,新建一个call对象
        Call call = sClient.newCall(request);
        try {
            Response response = call.execute();
            Log.d(TAG,response.toString());
            Log.d(TAG,"http get wx success");
            return true;
            //storageToken = response.body().string();
        } catch (IOException e) {
            Log.e(TAG,"httpGetWx exception");
            return false;
        }finally {
            //call
        }
        //return false;
    }
    /* 判断网络是否可用
     * <p>需添加权限 {@code <uses-permission android:name="android.permission.INTERNET"/>}</p>
     * <p>需要异步ping，如果ping不通就说明网络不可用</p>
     * @param ip ip地址（自己服务器ip），如果为空，ip为阿里巴巴公共ip
     * @return {@code true}: 可用<br>{@code false}: 不可用
     */
    public  boolean isAvailableByHttpGet() {
        if(httpGetWx() ==true){
            httpGetFailTimes = 0;
            NetLastTime = System.currentTimeMillis()  - ServiceStartTime;
        }else{
            httpGetFailTimes ++;
            if(httpGetFailTimes >3){
                if(mAutoReconect == true) {
                    WifiChanged = true;
                    setWifiStatus(DISABLE);
                    Log.d(TAG,"http get fail disableWifi ");
                }else{
                    HttpFailTimes ++;
                    //深度睡眠中途会中断wifi 等待到idle activer 状态时再启动wifi；
                    //isRunning = false;
                    Log.d(TAG,"http fail not auto restart " + HttpFailTimes);
                }
                httpGetFailTimes = 0;
                WifiThreadTimes = 0;
            }
        }
     return true;
    }

    /* 判断网络是否可用
     * <p>需添加权限 {@code <uses-permission android:name="android.permission.INTERNET"/>}</p>
     * <p>需要异步ping，如果ping不通就说明网络不可用</p>
     * @param ip ip地址（自己服务器ip），如果为空，ip为阿里巴巴公共ip
     * @return {@code true}: 可用<br>{@code false}: 不可用
     */
    public  boolean isAvailableByPing(String ip) {
        if (ip == null || ip.length() <= 0) {
            ip = "223.5.5.5";// 阿里巴巴公共ip
        }
        Runtime runtime = Runtime.getRuntime();
        Process ipProcess = null;
        try {
            PingStatus = false;
            //-c 后边跟随的是重复的次数，-w后边跟随的是超时的时间，单位是秒
            ipProcess = runtime.exec("/system/bin/ping -c 1 -w 3 " + ip);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    ipProcess.getInputStream()));
            int exitValue = ipProcess.waitFor();
            String line = null;
            while ((line = in.readLine()) != null) {
                Log.d(TAG,line);
                if(line.contains("time=")){
                    PingFailTimes = 0;
                    PingStatus = true;
                    int start = line.indexOf("time=");
                    int end = line.indexOf("ms");
                    String time = line.substring(start + 5, end -1);
                    float t = Float.parseFloat(time);
                    Log.d(TAG,"ping time = " + t);
                    NetLastTime = System.currentTimeMillis()  - ServiceStartTime;
                    if(t > WIFI_THRESHODE){
                        OverTimes ++;
                        Log.d(TAG,"wifi is not good times = " + OverTimes);
                        getWifiStatus();
                        if(OverTimes > 5){
                            if(mAutoReconect == true) {
                                setWifiStatus(DISABLE);
                                Log.d(TAG, "disableWifi ");
                                OverTimes = 0;
                                WifiChanged = true;
                            }
                        }
                    }else{
                        OverTimes = 0;
                    }
                }
            }
            if(PingStatus == false){
                PingFailTimes ++;
                if(PingFailTimes >5){
                    if(mAutoReconect == true) {
                        WifiChanged = true;
                        setWifiStatus(DISABLE);
                        Log.d(TAG,"ping fail disableWifi ");
                    }else{
                        isRunning = false;
                        Log.d(TAG,"ping fail not auto restart ");
                    }
                    PingFailTimes = 0;
                    WifiThreadTimes = 0;
                }
            }

            //  Message msg = handler.obtainMessage();
            //  msg.arg1 = WifiRestartTimes;
            //  handler.sendMessage(msg);
            return (exitValue == 0);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //在结束的时候应该对资源进行回收
            if (ipProcess != null) {
                ipProcess.destroy();
            }
            runtime.gc();
        }
        return false;
    }
}
