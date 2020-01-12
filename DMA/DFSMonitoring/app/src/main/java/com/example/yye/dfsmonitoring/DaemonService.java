package com.example.yye.dfsmonitoring;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

/**
 * Daemon service, keeps running till the end of all the tasks
 * */
public class DaemonService extends IntentService {
    private static final String TAG = "DFSDaemon";
    //variables are stored in myApplication
    private MyApplication myApplication;
    DetectionAlgorithmController detectionAlgorithmController;
    ExternalCommuController externalCommuController;
    PhoneBatteryManager phoneBatteryManager;
    BluetoothController bluetoothController;
    //a list to store content of the receiving log file
    public static List<byte[]> lst_content = new ArrayList<>();
    public static String LOG_FILE_NAME;

    /**
     * Constructor of the service
     */
    public DaemonService() {
        super("Daemon");
    }

    /**
     * on create, initiate all the components and myApplication,
     * register the bluetooth receiver
     */
    @Override
    public void onCreate() {
        myApplication = MyApplication.getInstance();
        super.onCreate();

        detectionAlgorithmController = new DetectionAlgorithmController();
        externalCommuController = new ExternalCommuController(getApplication());
        phoneBatteryManager = new PhoneBatteryManager(getApplication());
        bluetoothController = BluetoothController.getInstance();
        bluetoothController.registerBtReceiver(this);
    }

    /**
     *Start the service by handling a null intent
     * @param intent an Intent
     */
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(TAG,"Daemon begins.");
        startForeground(2,setNotification(this));// start Fore ground notification
        //start bluetooth controller
        bluetoothController.start();
        //start battery manager
        phoneBatteryManager.start();
        //start external communication controller
        externalCommuController.setDaemon(true);
        externalCommuController.start();
        //start detection controller
        //detectionAlgorithmController.start();

        //wait for the bluetooth connection and launch the StartActivity
        while (true){
            if (bluetoothController.isBltConnected()){

                if (!isActivityRunning(this,StartActivity.class.getName())){
                    startActivity(new Intent(this, StartActivity.class));
                }
                break;
            }
        }

        //a block to keep Daemon running
        while(true){
            //if the plane is took off
            if(myApplication.isTookOff()){
                //if the plane is landed
                if(myApplication.isLanded()){
                    //if the app has the permission to write to the external storage
                    if(myApplication.canWriteEx()){
                        //write log file
                        writeLogfile();
                        myApplication.setIsLogfileWritten(true);
                        break;
                    }
                    else{
                        //ask the UI to ask for permission
                        bluetoothController.sendHandlerMessage("",1012,BluetoothController.dataActHandler);
                    }
                }
            }
        }
        Log.d(TAG, "Daemon terminates.");
    }

    /**
     * make the service sticky
     */
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    /**
     * To write an array of byte to a file
     * @param fileName  name of file
     * @param byt the array of byte need to be written
     * @param firstWrite if it is the first time to write?
     * @return if write successfully returns true
     */
    public boolean writeFile(String fileName,byte[] byt,boolean firstWrite) {
        FileOutputStream fos;
        File file = new File(fileName);
        SystemClock.sleep(2000);
        try {
            if (!file.exists()){
                if (!file.createNewFile()) return false;
            }
            fos = new FileOutputStream(fileName,!firstWrite);
            fos.write(byt);
            fos.flush();
            fos.close();
            return true;
        } catch (IOException e) {
            Log.d(TAG, "saveFile failed: ", e);
        }
        return false;
    }

    /**
     * write the first element of lst_content into the log file if the list is not empty
     */
    public void writeLogfile(){
        while (true){
            boolean firstWrite = true;
            if(lst_content.size()!=0){
                File dir = createDir();
                while(true){
                    if(LOG_FILE_NAME != null && writeFile(dir.getAbsolutePath()+'/'+LOG_FILE_NAME,lst_content.remove(0),firstWrite)){
                        firstWrite = false;
                    }
                    if (lst_content.isEmpty()) break;
                }
                break;
            }
        }

    }

    /**
     * Create directory in an address if it does not exist
     * @return a File of directory
     */
    public File createDir(){
        File dir = new File(myApplication.getPath()+getPackageName());
        Log.d(TAG, "onHandleIntent: path:"+ dir.getAbsolutePath());
        if(!dir.exists()){
            try{
                if(dir.mkdir()){
                    Log.d(TAG, "run: The directory created: "+myApplication.getPath());
                }
            }catch (SecurityException e){
                Log.d(TAG, "run: The directory can not be created: "+myApplication.getPath());
            }
        }
        return dir;
    }

    /**
     * push a notification to UI
     * @param context the context from where the notification is set
     * @return a Notification
     */
    private Notification setNotification(Context context){// API >= 26
        //get a notification manager
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // If API>=26, set notification
        if (Build.VERSION.SDK_INT >= 26) {
            //specify notification channel
            NotificationChannel channel = new NotificationChannel("default", "Notification Channel", NotificationManager.IMPORTANCE_DEFAULT);
            if (notificationManager != null) { notificationManager.createNotificationChannel(channel);}

        //Configure what to do if the notification is clicked
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClass(context,StartActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, FLAG_UPDATE_CURRENT);

        //configure the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "default");
        builder.setContentTitle("On going")
                .setContentText("This notification will be dismissed after the monitoring.")
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true);
        return builder.build();
        }
       return null;
    }

    /**
     * Something to be done if the service is to be distroyed
     */
    @Override
    public void onDestroy(){
        Log.d(TAG,"Service terminates.");
        stopForeground(true);
        bluetoothController.unregisterBtReceiver(this);
        phoneBatteryManager.cancel();
        bluetoothController.turnOffBluetooth();
        super.onDestroy();
    }

    /**
     * Check if there is an activity running (UI is in the foreground or not)
     * @param mContext the context to get a service
     * @param className the activity name
     * @return
     */
    public static boolean isActivityRunning(Context mContext, String className) {
        boolean isRunning = false;
        ActivityManager activityManager = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager!=null){
            List<ActivityManager.RunningTaskInfo> activityList = activityManager.getRunningTasks(5);
            for (int i=0; i<activityList.size(); i++) {
                if (activityList.get(i).baseActivity.getClassName().equals(className)) {
                    isRunning = true;
                    break;
                }
            }
        }
        Log.d(TAG, "isActivityRunning: "+isRunning);
        return isRunning;
    }


    /**
     * Something to be done if the user slips off the app from the application task view
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (myApplication.isTookOff() && myApplication.isLanded() && myApplication.isLogfileWritten()) {
            Log.d(TAG, "onTaskRemoved: stop Daemon service.");
            stopService(new Intent(this,DaemonService.class));
        }
        Log.d(TAG, "onTaskRemoved: "+rootIntent.getAction());
    }

}
