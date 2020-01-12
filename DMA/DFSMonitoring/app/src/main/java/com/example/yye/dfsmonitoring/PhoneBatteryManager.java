package com.example.yye.dfsmonitoring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class PhoneBatteryManager extends Thread{
    //Int represented battery power status
    private static int batteryStatus;
    //Boolean represented if the phone is charging
    //Battery manager
    private BatteryManager batteryManager;
    //Receiver to receive broadcast about battery changes
    private BatteryReceiver batteryReceiver;
    private Context mContext;

    //Int represented different power status
    //battery level over 15%
    private static final int SUFFICIENT_POWER = 1;
    //battery level > 5% and <= 15%
    private static final int LOW_POWER = 0;
    //battery level <= 5%
    private static final int VERY_LOW_POWER = -1;

    //handler to update UI
    public static Handler mainActHandler;
    public static Handler dataActHandler;
    private static final String TAG = "DFSBtyManager";

    /**
     * Constructor of PhoneBatteryManager
     * @param context: passe the context through the instantiation
     */
    PhoneBatteryManager(Context context){
        mContext = context;
        //Initiate the battery manager
        batteryManager = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
    }

    /**
     * Register a receiver to receive battery broadcast.
     */
    @Override
    public void run() {
        //Registers receiver
        registerBatteryReceiver(mContext);
        //Initiate batteryStatus
        batteryStatus = getBatteryProperty();
       /*
        while (isRead){
            batteryStatus = getBatteryProperty();
            switch (batteryStatus){
                case LOW_POWER:
                    break;
                case DANGEROUS_POWER:
                    break;
                case SUFFICIENT_POWER:
                    break;
            }
        }
        */
    }

    /**
     * Registers a battery receiver with a corresponding filter.
     * @param context: pass a context for the registration
     */

    private void registerBatteryReceiver(Context context){
        //Receives a broadcast when battery is changed.
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        //Receives a broadcast when power is connected.
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        //Receives a broadcast when power is disconnected.
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        batteryReceiver = new BatteryReceiver();
        context.registerReceiver(batteryReceiver,filter);
        Log.d(TAG, "Register battery receiver.");
    }

    /**
     * Gets battery capacity.
     * @return int represented a battery status defined as the attributes of the class
     */
    private int getBatteryProperty(){
        int batteryProperty = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (batteryProperty > 15) return SUFFICIENT_POWER;
        else if (batteryProperty > 5) return LOW_POWER;
        else return VERY_LOW_POWER;    }

    /**
     * Cancels the thread
     */
    public void cancel(){
        //unregister the battery receiver.
        mContext.unregisterReceiver(batteryReceiver);
        Log.d(TAG, "Unregister battery receiver.");
    }

    /**
     * Send handler message to update UI.
     * @param string: a String put to the message.
     * @param what: a message code to identify the type of message
     * @param handler: the handler to whom a message will be sent.
     */
    private static void sendHandlerMessage(String string, int what, Handler handler){
        if(handler != null){
            Message msg = handler.obtainMessage(what);
            Bundle mBundle = new Bundle();
            mBundle.putString(CollectDataActivity.SHOW_MESSAGE,string);
            msg.setData(mBundle);
            handler.sendMessage(msg);
        }
    }

    /**
     * BatteryReceiver class extended from BroadcastReceiver
     * When the receiver receives and handles broadcast intents, onReceive() will be called.
     */
    public static class BatteryReceiver extends BroadcastReceiver{
        /**
         * Do different tasks when receiving different actions.
         * @param context: The Context in which the receiver is running.
         * @param intent: The Intent being received.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action!= null){
                switch (action){
                    //Battery changed.
                    case Intent.ACTION_BATTERY_CHANGED:
                        Log.d(TAG, "onReceive: battery changed.");
                        //calculate the battery percentage.
                        int current = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
                        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                        int percentage = current*100/scale;

                        //when battery goes down to 5%
                        if (percentage <= 5 && batteryStatus != VERY_LOW_POWER){
                            batteryStatus = VERY_LOW_POWER;
                            //make toast to ask user to charge the battery.
                            sendHandlerMessage("Battery percentage: " + percentage + "%", 1002, mainActHandler != null ? mainActHandler:dataActHandler);
                            sendHandlerMessage("Low power. Please charge your device.", 1002,  mainActHandler != null ? mainActHandler:dataActHandler);
                            // TODO: 13.12.2018  save app state,stop all the operations and wait.
                        }

                        //when battery is in the range of  5%-15%
                        else if ( percentage <= 15 && batteryStatus != LOW_POWER){
                            batteryStatus = LOW_POWER;
                            sendHandlerMessage("Battery percentage: " + percentage + "%", 1002, mainActHandler != null ? mainActHandler:dataActHandler);
                        }

                        //when battery goes up to 15%
                        else if ((percentage > 15 && batteryStatus != SUFFICIENT_POWER)){
                            batteryStatus = SUFFICIENT_POWER;
                            sendHandlerMessage("Power recovered.",1002, mainActHandler != null ? mainActHandler:dataActHandler);
                            // TODO: 13.12.2018  loads the saved state and resume operation
                        }
                        break;

                    //Power connected.
                    case  Intent.ACTION_POWER_CONNECTED:
                        //If battery is charging and under low power and very low power condition
                        if ( batteryStatus != SUFFICIENT_POWER){
                            sendHandlerMessage("Power recovered.",1002, mainActHandler != null ? mainActHandler:dataActHandler);
                            // TODO: 13.12.2018  loads the saved state and resume operation
                        }
                        Log.d(TAG, "onReceive: power connected");
                        break;

                    //Power disconnected.
                    case Intent.ACTION_POWER_DISCONNECTED:
                        //If battery is discharged and stil has very little power (<5%)
                        if (batteryStatus == VERY_LOW_POWER){
                            sendHandlerMessage("Low power. Please charge your device.", 1002,  mainActHandler != null ? mainActHandler:dataActHandler);
                            // TODO: 13.12.2018  save app state,stop all the operations and wait.
                        }
                        Log.d(TAG, "onReceive: power disconnected");
                        break;

                }
            }

        }
    }
}
