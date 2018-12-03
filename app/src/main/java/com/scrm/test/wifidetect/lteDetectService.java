package com.scrm.test.wifidetect;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class lteDetectService extends Service {
    public static final String TAG = "LTE_DETECT";
    private Messenger activityMessenger;
    private static volatile  boolean isRunning;
    private lteDetectService.MessengerHandler messengerHandler;
    private Context mContext;
    private static long ServiceStartTime = 0; //本次连接wifi 启动时间
    private static boolean LteChanged = false;//ping 超过一定时间的超时次数
    private static int PingFailTimes = 0; // wifi ping 失败次数 连续3次Ping 失败，则重启wifi
    private static boolean PingStatus = false; // ping 执行结果，false 表示ping 失败
    private static int LteRestartTimes = 0;
   // private static long ServiceStartTime = 0; //本次连接wifi 启动时间
    private static long NetLastTime = 0; // wifi持续时间
    private static final int  PING_THRESHODE = 3000;// wifi ping 测试时间阈值，大于500表示wifi模块功能异常,连续5次。要重启wifi
    public LteStatus mLteStatus =  new LteStatus();
    private static int OverTimes = 0;//ping 超过一定时间的超时次数
    public static int LteThreadTimes = 0;
    static final int WIFI = 1;
    static final  int LTE = 2;
    public class LteStatus{
        String powerOnTime;
        String wifiLastTime;
    }
    public lteDetectService(){
        messengerHandler = new MessengerHandler();
    }
    private class MessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.replyTo != null) {
                activityMessenger = msg.replyTo;
                notifyActivity();
            }
            super.handleMessage(msg);
        }
    }
    public String millsecondToString(long millsecond){
        int totalSeconds = (int) (millsecond / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hour = (totalSeconds/(60*60))%24;
        String time = hour > 0 ?String.format("%02dh%02dm%02ds",hour,minutes,seconds) :
                String.format("%02dm%02ds",minutes,seconds);
        return time;
    }
    public static boolean isNetSystemUsable(Context context){
        boolean isNetUsable = false;
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            NetworkCapabilities networkCapabilities =
                    manager.getNetworkCapabilities(manager.getActiveNetwork());
/*            if(networkCapabilities != null) {
                isNetUsable = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }*/
            isNetUsable = (networkCapabilities == null ? false : networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        }
        return isNetUsable;
    }

    private void notifyActivity(){
        isRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(isRunning) {
                    if( isNetSystemUsable(mContext)){
                        Log.d(TAG,"NetSystemUsable is true");
                    }else {
                        Log.d(TAG,"NetSystemUsable is false");
                    }
                    try {
                        if(LteChanged == false) {
                            isAvailableByPing("www.baidu.com");
                            long runTime = SystemClock.elapsedRealtime();
                            String powerOnTime = millsecondToString(runTime);
                            String wifiLastTime = millsecondToString(NetLastTime);
                            mLteStatus.powerOnTime = powerOnTime;
                            mLteStatus.wifiLastTime = wifiLastTime;
                        }else {
                           // setWifiStatus(ENABLE);
                            LteRestartTimes++;
                            LteChanged = false;
                            Thread.sleep(10000);
                        }
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // count++;
                    Message message = Message.obtain();
                    message.what = LTE;
                    message.arg1 = LteRestartTimes;
                    message.obj = mLteStatus;

                    try {
                        activityMessenger.send(message);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
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
            String line = null;
            while ((line = in.readLine()) != null) {
                if(line.contains("time=")){
                    PingFailTimes = 0;
                    PingStatus = true;
                    int start = line.indexOf("time=");
                    int end = line.indexOf("ms");
                    String time = line.substring(start + 5, end -1);
                    float t = Float.parseFloat(time);
                    Log.d(TAG,"ping time = " + t);
                    NetLastTime = System.currentTimeMillis()  - ServiceStartTime;
                    if(t > PING_THRESHODE){
                        OverTimes ++;
                        Log.d(TAG,"lte is not good times = " + OverTimes);
                       // getWifiStatus();
                        if(OverTimes > 5){
                           // setWifiStatus(DISABLE);
                            Log.d(TAG,"disablelte ");
                            OverTimes = 0;
                            LteChanged = true;
                        }
                    }else{
                        // Message msg = handler.obtainMessage();
                        // msg.arg1 = 2222;
                        // handler.sendMessage(msg);
                        OverTimes = 0;
                    }
                }
            }
            if(PingStatus == false){
                PingFailTimes ++;
                if(PingFailTimes >3){
                    //setWifiStatus(DISABLE);
                    Log.d(TAG,"ping fail disableWifi ");
                    PingFailTimes = 0;
                    LteChanged = true;
                    LteThreadTimes = 0;
                }
            }
            int exitValue = ipProcess.waitFor();
            //  Message msg = handler.obtainMessage();
            //  msg.arg1 = LteRestartTimes;
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

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG,"lte detect service  start");
        mContext = this;

        int model = intent.getIntExtra("model",1);
        ServiceStartTime = System.currentTimeMillis();
        return new Messenger(messengerHandler).getBinder();

    }
    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG,"lte detect service  onUnbind");
        isRunning = false;
        return super.onUnbind(intent);
    }
}
