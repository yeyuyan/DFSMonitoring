package com.example.yye.dfsmonitoring;

import android.util.Log;

/**
 * This class is to detect take-off and landing event automatically
 */
public class DetectionAlgorithmController extends Thread{

    String TAG = "DFSDeCtl";
    private int craftState;
    private SensorManager mSensorMangager = new SensorManager();
    @Override
    public void run(){
        Log.d(TAG,"Detection begins.");
        detectCraftState();
        Log.d(TAG,"Detection finished");
    }

    /**
     * To detect aircraft state
     * Now we fake a take-off event and a landing event in 20s
     */
    private void detectCraftState(){
        // TODO: 09.10.2018 Algorithm to detect the take-off and landing events
        int NORMAL = 0;
        int TAKE_OFF = 1;
        int LANDING = 2;
        craftState = NORMAL;

        int time = 0;//just for test
        while (true){
            //Begin detection

            time+=1;//just for test

            if(time == 1){//takeOff detected
                craftState = TAKE_OFF;
                Log.d(TAG,"take-off detected");
            }
            if(time == 20 ){//landing detected
                craftState = LANDING;
                Log.d(TAG,"landing detected");
                break;
            }
        }
    }

    /**
     * To get the aircraft state
     * @return a int represented aircraft state
     */
    public int getCraftState(){
        return craftState;
    }
    /*Auto detection of take-off and landing events.*/
}

