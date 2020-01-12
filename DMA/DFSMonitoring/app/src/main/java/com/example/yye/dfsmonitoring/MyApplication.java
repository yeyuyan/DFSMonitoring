package com.example.yye.dfsmonitoring;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Base class for maintaining global application state.
 * There are all the variables storing the app info.
 * This class will be built first just after the app is launched.
 * These variables is for app configurations.
 * You can also find the instance of the class in every file I built.
 * (one file stands for one component in the structure except the UI)
 */
public class MyApplication extends Application {
    // If the plane is took off
    private static boolean isTookOff = false;
    // If the plane is landed
    private static boolean isLanded = false;
    // If the log file is written
    private static boolean isLogfileWritten = false;
    // If the flight data is sent
    private static boolean isFlightDataSent = false;
    // If the notification is set
    public boolean isNoticeSet = false;
    // a list of the running activities
    public List<Activity> activityList = new LinkedList<>();

    @SuppressLint("StaticFieldLeak")
    private static MyApplication myApplication;
    private static final String TAG = "DFSMyApplication:";

    /*
    Following functions provide the possibility to get the app info from the variables above
    and a way to set their value.
     */
    public boolean isTookOff(){
        return isTookOff;
    }
    public void setIsTookOff(boolean b) {
        MyApplication.isTookOff = b;
    }

    public boolean isLanded(){
        return isLanded;
    }
    public void setIsLanded(boolean b) {
        isLanded = b;
    }

    public boolean isFlightDataSent(){
        return isFlightDataSent;
    }
    public void setIsFlightDataSent(boolean b) {
        isFlightDataSent = b;
    }

    public boolean isLogfileWritten(){
        return isLogfileWritten;
    }
    public void setIsLogfileWritten(boolean b){
        isLogfileWritten = b;
    }

    /**
     * Get the external storage path
     */
    public String getPath(){
        return Environment.getExternalStorageDirectory().getPath() + "/Android/data/";
    }

    /**
     * Get the package name
     */
    public String getPkgName(){
        return getPackageName()+"/";
    }

    /**
     * This function will be first executed when MyApplication is called.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init();
        myApplication = this;
    }

    /**
     * To create the app's directory in the external storage if it doesn't exist.
     * @return the path of the directory
     */
    public String createDir(){
        File dir = new File(myApplication.getPath()+getPackageName());
        if(!dir.exists()){
            Log.d(TAG, "createDir: This directory doesn't exist: "+dir.getAbsolutePath());
            try{
                if(dir.mkdirs()){
                    Log.d(TAG, "createDir: The directory is created");
                }

            }catch (SecurityException e){
                Log.d(TAG, "createDir: The directory can not be created: "+myApplication.getPath());
            }
        }
        else{
            Log.d(TAG, "createDir: This directory is already exist: "+dir.getAbsolutePath());
        }
        return dir.getAbsolutePath();
    }

    /**
     * To get an instance of the class
     */
    public static MyApplication getInstance(){
        return myApplication;
    }

    /**
     * To save logcat info in a file.
     */
    public void saveLogcatToFile() {
        String dirPath = createDir();
        File outputFile = new File(dirPath+"/"+"Log_android.log");
        try {
            if(outputFile.exists()){
                if (!outputFile.delete()) Log.e(TAG, "saveLogcatToFile: previous file can't be deleted.");
            }
            if(outputFile.createNewFile()){
                Runtime.getRuntime().exec(new String[]{"logcat","DFSDataAct:D","DFSBltAct:D","DFSDaemon:D",
                        "DFSBltCtl:D","DFSDeCtl:D","DFSExtCommuCtl:D","DFSSensManager:D","DFSbroadcast:D",
                        "DFSbtService:D","DFSBtyManager:D","*:S","-f", outputFile.getAbsolutePath()});
            }
            Log.d(TAG, "Save logcat to : "+dirPath+"/"+"Log_android.log");
        }
        catch (IOException e)
        {
            Log.e(TAG, "saveLogcatToFile: ",e);
        }
    }

    /**
     * Add an activity to the list
     * @param activity the activity to be added
     */
    public void addActivity(Activity activity)
    {
        activityList.add(activity);
    }

    /**
     * Kill the activities in the list(kill the GUI)
     */
    public void exit() {
        for(Activity activity : activityList) {
            activity.finish();
        }
    }

    /**
     * To see if the app has the bluetooth permission
     * @return a boolean
     */
    public boolean hasBtPermission(){
        return Build.VERSION.SDK_INT < 23 || (PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION));
    }
    /**
     * To see if the app has the permission to read the phone state
     * @return a boolean
     */
    public boolean canRead(){
        return (PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE));
    }

    /**
     * To see if the app has the permission to write in the external storage
     * @return a boolean
     */
    public boolean canWriteEx(){
        return (Build.VERSION.SDK_INT <= 23 || ActivityCompat.checkSelfPermission(this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * request permissions in a given activity
     * @param activity a Activity
     * @param permissionStr the permission in String
     */
    public void requestPermission(Activity activity,String permissionStr){
        List<String> permission = new ArrayList<>();
        permission.add(permissionStr);
        ActivityCompat.requestPermissions(activity,permission.toArray(new String[1]),1);
    }

    /**
     * To see if the Daemon is running. Will be called when the StartActivity is launched.
     * @param mContext the Context where we get an ActivityManager
     * @param className the service class name
     * @return a boolean
     */
    public boolean isServiceRunning(Context mContext, String className) {
        boolean isRunning = false;
        ActivityManager activityManager = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager!=null){
            List<ActivityManager.RunningServiceInfo> serviceList = activityManager.getRunningServices(5);
            for (int i=0; i<serviceList.size(); i++) {
                Log.d(TAG, "isServiceRunning: serviceName: "+serviceList.get(i).service.getClassName());
                if (serviceList.get(i).service.getClassName().equals(className)) {
                    isRunning = true;
                    break;
                }
            }
        }
        Log.d(TAG, "isServiceRunning: "+className+" is running: "+isRunning);
        return isRunning;
    }


    /**
     * To define the loading dialog
     *
     * @param context the context the dialog belongs to
     * @param message the message to be shown
     * @return a Dialog
     */
    public Dialog loadingDialog(Context context, String message){
        Log.d(TAG, "loadingDialog: start");
        LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(R.layout.dialog_loading,null);
        ConstraintLayout layout = v.findViewById(R.id.dialog_connecting);
        TextView tv_msg = layout.findViewById(R.id.tipText);
        tv_msg.setText(message);

        Dialog dialog = new Dialog(context,R.style.MyDialogStyle);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(layout,new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT));

        //Wrap the method that shows the dialog
        Window window = dialog.getWindow();
        if (window != null) {
            Log.d(TAG, "loadingDialog: window not null");
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setGravity(Gravity.CENTER);
            window.setAttributes(lp);
            window.setWindowAnimations(R.style.PopWindowAnimStyle);
            dialog.show();
        }
        else{
            Log.d(TAG, "loadingDialog: window null");
        }
        return dialog;
    }

    /**
     * close a dialog
     * @param dialog the dialog to be closed
     */
    public static void closeDialog(Dialog dialog) {
        if (dialog != null && dialog.isShowing()) {
            Log.d(TAG, "closeDialog: dialog closed.");
            dialog.dismiss();
        }
    }


}
