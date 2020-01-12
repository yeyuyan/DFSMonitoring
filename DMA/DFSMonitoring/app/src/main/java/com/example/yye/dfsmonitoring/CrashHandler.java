package com.example.yye.dfsmonitoring;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.Date;

/**
 * This class records the uncaught exception that breaks down the application
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static CrashHandler crashHandler = new CrashHandler();
    private MyApplication myApplication = MyApplication.getInstance();
    private static final String TAG = "uncaughtException";

    /**
     * If there is a crash
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        if (crashHandler != null) {
            try {
                //write crash log into a file
                File exceptionLog = new File(myApplication.getPath()+"/com.example.yye.dfsmonitoring" +"/uncaughtException.log");
                if (!exceptionLog.exists()) {exceptionLog.createNewFile();}
                FileOutputStream fileOutputStream = new FileOutputStream(exceptionLog);
                Date currentTime = Calendar.getInstance().getTime();
                fileOutputStream.write((currentTime.toString()+' ').getBytes() );
                PrintStream printStream = new PrintStream(fileOutputStream);
                e.printStackTrace(printStream);
                printStream.flush();
                printStream.close();
                fileOutputStream.close();
            } catch (FileNotFoundException ex) {
                Log.d(TAG, ex.toString());
            } catch (IOException ex) {
                Log.d(TAG, ex.toString());
            }
        }
    }

    /**
     * set up default handler
     */
    public void init() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    private CrashHandler() {}

    /**
     * get an instance of CrashHandler
     * @return the crashHandler of the class
     */
    public static CrashHandler getInstance() {
        return crashHandler;
    }
}

