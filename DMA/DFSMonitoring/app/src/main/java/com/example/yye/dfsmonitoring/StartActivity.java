package com.example.yye.dfsmonitoring;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The activity to be activated once the app is launched.
 * There is a button to enter the CollectDataActivity
 */
public class StartActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "DFSMainAct";
    private MyApplication myApplication;
    BluetoothController bluetoothController;
    // for request of permissions
    int REQUEST_CODE = 1;
    //the message passed by the handler will be stored in this String
    public static String SHOW_MESSAGE;
    //Handler to handler messages
    Handler handler;
    //UI elements
    Button buttonToStart;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myApplication = MyApplication.getInstance();
        myApplication.addActivity(this);
        bluetoothController=BluetoothController.getInstance();
        //init UI
        UIInit();
        //request permissions
        requestPermission("Permission Request", "To use Bluetooth, " +
                "get your mobile phone id and store received files, " +
                "related permissions are needed. Thank you for your cooperation.");
        //launch Daemon if it is not running
        if(!myApplication.isServiceRunning(this,DaemonService.class.getName())){
            startService(new Intent(this, DaemonService.class));
        }
        //set handler
        handler=setHandler();
        Log.d(TAG, "start UI interface");
    }

    /**
    Initiate the UI layout
     */
    public void UIInit(){
        //find the UI elements
        textView = findViewById(R.id.textView2);
        buttonToStart = findViewById(R.id.buttonToStart);
        //listen to the "click" event
        buttonToStart.setOnClickListener(this);
        //If blt is connected, enable the button
        buttonToStart.setEnabled(bluetoothController.isBltConnected());
    }

    /**
     * To show a toast on the UI
     * @param text the text to be shown
     */
    public void makeToast(String text){
        Toast toast = Toast.makeText(getApplication(),text ,Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM,0,80);
        toast.show();
    }

    /**
     *  Start the CollectDataActivity when the button is clicked.
     */
    @Override
    public void onClick(View view) {
        Intent intent = new Intent(this,CollectDataActivity.class);
        startActivity(intent);
    }

    /**
     * Shows a dialog to request the permissions in one time that the app doesn't have.
     * @param title Title of the dialog
     * @param content Content of the dialog
     */
    public void requestPermission(String title, String content){
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.READ_PHONE_STATE,Manifest.permission.WRITE_EXTERNAL_STORAGE};
        final List<String> permissionList = new ArrayList<>();
        if(!myApplication.hasBtPermission()) permissionList.add(permissions[0]);
        if (!myApplication.canRead()) permissionList.add(permissions[1]) ;
        if (!myApplication.canWriteEx()) permissionList.add(permissions[2]);

        if(!permissionList.isEmpty()){
            new android.support.v7.app.AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(content)
                    //click the yes button in the dialog
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        /**
                         *  request of permissions
                         */
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(StartActivity.this, permissionList.toArray(new String[permissionList.size()]),REQUEST_CODE);
                            Log.d(TAG, "request permission");
                        }
                    })
                    //click the no button
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        /**
                         * exit the application
                         */
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                            myApplication.exit();
                        }
                    }).setCancelable(false).show();
        }
    }

    /**
     * Deal with the results of the request of permissions
     * @param requestCode 1
     * @param permissions list of String of permissions
     * @param grantResults a list of int, one element representing one result
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                for (int i=0; i < grantResults.length; i++){
                    //For the permissions that are granted
                    if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                        Log.d(TAG, "Permission: " + permissions[i] + " granted");
                        //if the app can write in the external storage, write the log in a file
                        if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            myApplication.saveLogcatToFile();
                    }
                    // For the permissions not granted
                    else if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)){
                        //if the blt permission not granted, exit the app
                        myApplication.exit();
                    }
                }
            }
        }
    }


    /**
     * set handler for the activity
     * @return
     */
    private Handler setHandler(){
        handler = new MyHandler(this);
        BluetoothController.mainActHandler = handler;
        ExternalCommuController.mainActHandler = handler;
        PhoneBatteryManager.mainActHandler = handler;
        return handler;
    }

    /**
     * Define MyHandler (the Handler we want)
     */
    public static class MyHandler extends Handler {
        //Here weakReference is needed to avoid the memory leak
        private final WeakReference<StartActivity> mActivity;
        Dialog dialog = null;
        TextView tv = null;

        MyHandler(StartActivity activity){
            mActivity = new WeakReference<>(activity);
        }

        /**
         * When receives a message, do the corresponding UI update according to the what code of it
         * @param msg a Message from other entities
         */
        @Override
        public void handleMessage(Message msg){
            StartActivity activity = mActivity.get();
            Bundle bundle = msg.getData();
            String str_msg = bundle.getString(SHOW_MESSAGE);
            switch (msg.what){
                // show toast to the UI
                case 1002:
                    activity.makeToast(str_msg);
                    break;
                //change the text of the textView
                case 1003:
                    activity.textView.setText(str_msg);
                    break;
                //show a dialog
                case 1004:
                    if (dialog == null) {
                        dialog = activity.myApplication.loadingDialog(activity,str_msg);
                        dialog.show();
                        tv = dialog.findViewById(R.id.tipText);
                    }
                    break;
                //close a dialog
                case 1005:
                    MyApplication.closeDialog(dialog);
                    break;
                //Enable a button to be pressed
                case 1008:
                    activity.buttonToStart.setEnabled(true);
            }
        }
    }

    /**
     * release the bluetoothController when the activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        bluetoothController = null;
    }

    /**
     * Save the UI state when it is not in the foreground
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("text",textView.getText().toString());
        super.onSaveInstanceState(savedInstanceState);
    }
}


