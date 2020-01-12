package com.example.yye.dfsmonitoring;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONStringer;

import java.lang.ref.WeakReference;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;


/**
 * This Activity collects flight data and take-off and landing events
 */
public class CollectDataActivity extends AppCompatActivity implements View.OnClickListener {

    private MyApplication myApplication;
    public static String TAG = "DFSDataAct";
    //UI elements
    Button btSendData,btCheck,btMonitoring;
    EditText flightNum,departure,destination,airline;
    AlertDialog alertDialog;

    BluetoothController bluetoothController;
    Handler handler = null;
    public static String SHOW_MESSAGE;

    /**
     * On create, if bluetooth is not connected, return to the StartActivity
     * (that will happen if the service crashes but the GUI remains)
     * If connected, set the handler and initiate UI.
     * Then check if the app sends the flight data or not
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        myApplication = MyApplication.getInstance();
        myApplication.addActivity(this);
        bluetoothController = BluetoothController.getInstance();

        if(bluetoothController.isBltConnected()){
            handler = new MyHandler(this);
            setContentView(R.layout.collect_data_activity);
            UIInit(savedInstanceState);
            handler = setHandler();
            // If landed but the flight data not be sent, show a toast to tell user
            if (myApplication.isTookOff() && myApplication.isLanded()&& !myApplication.isFlightDataSent())
                makeToast("lack of data");
        }
        else {
            myApplication.exit();
            startActivity(new Intent(CollectDataActivity.this,StartActivity.class));
        }
    }

    /**
     * Save the UI state when it is not in the foreground
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {

        savedInstanceState.putBoolean("btCheck",btCheck.isEnabled());
        savedInstanceState.putBoolean("btSendData",btSendData.isEnabled());
        savedInstanceState.putBoolean("editText",flightNum.isEnabled());
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * set the handler
     * @return a Handler
     */
    private Handler setHandler(){
        handler = new MyHandler(this);
        BluetoothController.dataActHandler = handler;
        ExternalCommuController.dataActHandler = handler;
        PhoneBatteryManager.dataActHandler = handler;
        PhoneBatteryManager.mainActHandler = null;
        BluetoothController.mainActHandler = null;
        ExternalCommuController.mainActHandler = null;
        return handler;
    }

    /**
     * Initiate UI
     * @param savedInstanceState the UI state need to be recovered
     */
    public void UIInit(Bundle savedInstanceState ){
        btSendData = findViewById(R.id.btSendData);
        btSendData.setEnabled(false);
        btSendData.setOnClickListener(CollectDataActivity.this);
        btCheck = findViewById(R.id.btCheck);
        btCheck.setOnClickListener(CollectDataActivity.this);
        airline = findViewById(R.id.AirlineText);
        flightNum = findViewById(R.id.FlightNumText);
        departure = findViewById(R.id.DepartureText);
        destination = findViewById(R.id.DestinationText);

        btMonitoring = findViewById(R.id.btStartM);
        if (!myApplication.isTookOff()){
            btMonitoring.setText(R.string.StartM);
        } else if (!myApplication.isLanded())
            btMonitoring.setText(R.string.EndM);
        else btMonitoring.setText(R.string.Landed);

        if (savedInstanceState!=null){
            btSendData.setEnabled(savedInstanceState.getBoolean("btSendData"));
            btCheck.setEnabled(savedInstanceState.getBoolean("btCheck"));
            airline.setEnabled(savedInstanceState.getBoolean("editText"));
            flightNum.setEnabled(savedInstanceState.getBoolean("editText"));
            departure.setEnabled(savedInstanceState.getBoolean("editText"));
            destination.setEnabled(savedInstanceState.getBoolean("editText"));
        }
    }

    /**
     * set a confirmation dialog of the take-off event
     */
    private Builder setToffBuilder(final Button bt){
        Builder builder1 = new Builder(this);
        builder1.setTitle("Take-off event");
        builder1.setMessage("Is the plane taking off?");
        // get a Take-off event
        builder1.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //start a connection check
                bluetoothController.testConnection();
                //get take-off time
                long DtTmTkoff = (System.currentTimeMillis())/1000;
                try {
                    String str = new JSONStringer().object()
                            .key("type").value(1)
                            .key("DtTmTkoff").value(DtTmTkoff)
                            .endObject().toString();
                    //add the event data to a list in the bluetoothController
                    bluetoothController.sendLst.add(str);
                    bt.setEnabled(false);
                    bt.setText(R.string.sending);
                } catch (JSONException e) {
                    Log.e(TAG, "onClick: ",e);
                }
            }
        });

        builder1.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) { }
        });
        return builder1;
    }

    /**
     *set a confirmation dialog of the landing event
     */
    private Builder setLdgBuilder(final Button bt){
        Builder builder2 = new Builder(this);
        builder2.setTitle("Landing event");
        builder2.setMessage("Is the plane landing?");
        // get a landing event
        builder2.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //start a connection check
                bluetoothController.testConnection();
                //get landing time
                long DtTmLdg = (System.currentTimeMillis())/1000;
                try {
                    String str = new JSONStringer().object()
                            .key("type").value(2)
                            .key("DtTmLdg").value(DtTmLdg)
                            .endObject().toString();
                    //add the event data to a list in the bluetoothController to be sent
                    bluetoothController.sendLst.add(str);
                    bt.setEnabled(false);
                    bt.setText(R.string.sending);
                } catch (JSONException e) {
                    Log.e(TAG, "onClick: ",e);
                }
            }
        });

        builder2.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which){}
        });
        return builder2;

    }

    /**
     * When clicking the monitoring button, show a dialog to confirm
     */
    public void monitoringClick(View view) {
        if (!myApplication.isTookOff()){
            Builder builder1 = setToffBuilder(btMonitoring);
            alertDialog = builder1.create();
            alertDialog.show();

        } else if(!myApplication.isLanded()) {
            Builder builder2 = setLdgBuilder(btMonitoring);
            alertDialog = builder2.create();
            alertDialog.show();
        }
    }

    /**
     * make toast to UI
     */
    public void makeToast(String text){
        Toast toast = Toast.makeText(this,text ,Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM,0,80);
        toast.show();
    }

    /**
     * Get the phone IMEI number
     */
    public String getIMEI(){
        TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        try{
            if (telephonyManager != null)
                return telephonyManager.getDeviceId();
        }catch (SecurityException e){
            Log.d(TAG,"Security Exception to get IMEI Num.",e);
        }
        return null;
    }

    /**
     * Check if the characters in a string are all capital
     * @param str the string to be checked
     * @return a boolean
     */
    public Boolean isUpperCase(String str){
        Character c;
        for (int i = 0;i <str.length(); i++){
            c = str.charAt(i);
            if (!Character.isUpperCase(c)){
                makeToast("Please enter capital letters!");
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the flight data input is in correct form
     * @param str the data to be checked
     * @param inputType the type of data
     * @return a boolean
     */
    public Boolean checkInput(String str,int inputType){
        int lens = str.length();
        switch (inputType){
            case 0: //check the airport name
                if (lens != 3){
                    makeToast("Please enter three letters for the airport name.");
                    return false;
                }
                else{
                    return isUpperCase(str);
                }

            case 1://check the airline name
                if (lens >3) {
                    makeToast("Please enter the airline name in no more than three letters.");
                    return false;
                }
                else if (str.isEmpty()){
                    makeToast("Please enter the airline name.");
                    return false;
                }
                else {
                    return isUpperCase(str);
                }

            case 2://check the flight number
                if(lens >4){
                    makeToast("The flight number should be in less than four digits");
                    return false;
                }
                else if(lens <1){
                    makeToast("Please enter the flight number");
                    return false;
                }
        }
        return true;
    }

    /**
     * When a button is clicked
     * @param view the button
     */
    public void onClick(View view){
        //get the text input
        String airlineStr = airline.getText().toString();
        String flightNumStr = flightNum.getText().toString();
        String departureStr = departure.getText().toString();
        String destinationStr = destination.getText().toString();

        switch (view.getId()){
            //check data form button
            case R.id.btCheck:
                if(myApplication.isFlightDataSent()) {
                    makeToast("You have already enter the flight data.");
                    break;
                }
                //check data form
                if (checkInput(airlineStr,1)
                        && checkInput(flightNumStr,2)
                        && checkInput(departureStr,0)
                        && checkInput(destinationStr,0)){
                    btSendData.setEnabled(true);
                    btCheck.setEnabled(false);
                    makeToast("Data in correct form.");
                }
                break;
            //send data button
            case R.id.btSendData:
                if (!myApplication.canRead()) {
                    myApplication.requestPermission(CollectDataActivity.this,Manifest.permission.READ_PHONE_STATE);
                    makeToast("Try it again after giving the permission.");
                    break;
                }
                //start the connection check
                bluetoothController.testConnection();
                int fNumber = Integer.parseInt(flightNumStr);
                //get flight data
                try{
                    String flightData = new JSONStringer().object()
                            .key("type").value(0)
                            .key("IATA_number").value(airlineStr+fNumber)
                            .key("DepAirp").value(departureStr)
                            .key("ArrAirp").value(destinationStr)
                            .key("Device_ID").value(Long.parseLong(getIMEI()))
                            .endObject().toString();
                    //put data into the list in bluetoothController
                    bluetoothController.sendLst.add(flightData);
                    btSendData.setEnabled(false);
                }catch (JSONException e){
                    Log.e(TAG, "onClick: json exception:",e);
                }
                break;
        }
    }

    /**
     * release the bluetoothController when the activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy.");
        bluetoothController = null;
    }

    /**
     * set notification
     * @param title title of notification
     * @param text text to show
     * @return a boolean
     */
    public boolean setNotification(Context context,Class activityClass,String title,String text){// API >= 26

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("default", "Notification Channel", NotificationManager.IMPORTANCE_DEFAULT);
            if (notificationManager != null){ notificationManager.createNotificationChannel(channel);}
        }
        Intent intent = new Intent(context,activityClass);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0,intent,FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,"default");
        builder.setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher);
        if (notificationManager != null) {
            notificationManager.notify(1,builder.build());
        }
        return true;
    }

    /**
     * cancel notification
     */
    public void cancelNotification(){
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(1);
        }
    }

    /**
     * Define the handler for this activity
     */
    public static class MyHandler extends Handler {
        private final WeakReference<CollectDataActivity> mActivity;
        Dialog dialog = null;
        TextView tv = null;

        MyHandler(CollectDataActivity activity){
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg){
            CollectDataActivity activity = mActivity.get();
            Bundle bundle = msg.getData();
            String str_msg = bundle.getString(SHOW_MESSAGE);
            switch (msg.what){
                //make toast
                case 1002:
                    activity.makeToast(str_msg);
                    break;
                //set notification for lack of data
                case 1003:
                    activity.myApplication.isNoticeSet = activity.setNotification(activity,CollectDataActivity.class,
                            "Lack of flight data","Please enter the flight data!");
                    break;
                //show a dialog
                case 1004:
                    if (dialog == null) {
                        dialog = activity.myApplication.loadingDialog(activity,str_msg);
                        tv = dialog.findViewById(R.id.tipText);
                    }else if (dialog.isShowing()){
                        tv.setText(str_msg);
                    }
                    break;
                //close dialog
                case 1005:
                    SystemClock.sleep(1000);
                    MyApplication.closeDialog(dialog);
                    break;
                //set notification for network problem
                case 1006:
                    activity.myApplication.isNoticeSet = activity.setNotification(activity,CollectDataActivity.class,
                            "No network available",
                            "Please connect to WIFI or Data for transferring the data file.");
                    break;
                //cancel notification
                case 1007:
                    if (activity.myApplication.isNoticeSet) activity.cancelNotification();
                    break;
                //change button's text and enable it
                case 1008:
                    if (activity.myApplication.isTookOff()){
                        activity.btMonitoring.setText(R.string.EndM);
                    }
                    if (!activity.btMonitoring.isEnabled()){
                        activity.btMonitoring.setEnabled(true);
                        activity.makeToast(str_msg);
                    }
                    break;
                //change button's text and enable it
                case 1009:
                    if(activity.myApplication.isLanded()){
                        activity.btMonitoring.setText(R.string.Landed);
                    }
                    if (!activity.btMonitoring.isEnabled()){
                        activity.btMonitoring.setEnabled(true);
                        activity.makeToast(str_msg);
                    }
                    break;
                //deactivate the inputs if the flight data is sent
                case 1011:
                    if(activity.myApplication.isFlightDataSent()){
                        activity.btSendData.setEnabled(false);
                        activity.flightNum.setEnabled(false);
                        activity.airline.setEnabled(false);
                        activity.destination.setEnabled(false);
                        activity.departure.setEnabled(false);
                        activity.btSendData.setText(R.string.Success);
                    }
                    break;
                //request permission
                case 1012:
                    if (!activity.myApplication.canWriteEx()) {
                        activity.myApplication.requestPermission(activity,Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    }
            }
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
                    if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                        Log.d(TAG, "Permission: " + permissions[i] + " granted");
                        if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            myApplication.saveLogcatToFile();
                    }
                }
            }
        }
    }
}
