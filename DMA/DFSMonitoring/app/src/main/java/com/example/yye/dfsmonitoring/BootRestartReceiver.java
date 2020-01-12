package com.example.yye.dfsmonitoring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * This class is to receive the boot finished broadcast and start the foreground service
 */
public class BootRestartReceiver extends BroadcastReceiver {
    private final static String TAG = "DFSbroadcast";

    /**
     * When we receive the broadcast
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action!=null){
            if (action.equals(Intent.ACTION_BOOT_COMPLETED)){
                Log.d(TAG,"onReceive: "+action);
                //android version higher or equal to Android O
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    //start Daemon
                    context.startForegroundService(new Intent(context, DaemonService.class));
                    Log.d(TAG, "Called Daemon.");
                }
                else{
                     //TODO: 15.11.2018 Solution for android API <= 26
                    context.startService(new Intent(context,DaemonService.class));
                }
            }
        }
    }
}
