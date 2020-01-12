package com.example.yye.dfsmonitoring;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONStringer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;

/**
 * Bluetooth Controller that controls the connection
 * and communication(read and write) with DEBA
 */
public class BluetoothController extends Thread{
    private static final String BLUETOOTH_NAME = "raspberrypi";
    // Handlers for activities for updating UI
    public static Handler mainActHandler;
    public static Handler dataActHandler;
    //Client(here is Android phone) socket
    private BluetoothSocket mClientSocket;
    //Bluetooth device
    private BluetoothDevice mDevice;
    //Bluetooth adapter
    private BluetoothAdapter bluetoothAdapter;
    // Broadcast receiver to receive bluetooth broadcast
    private BroadcastReceiver btReceiver = null;
    //Instance of class
    private static BluetoothController bluetoothController;
    //Thread for connection
    private ConnectThread mConnectThread;
    //Thread for communication
    private CommunicationThread mCommuThread;
    private MyApplication myApplication;

    /*
    //Bluetooth status
    private static final int BLT_DISABLED = 0;
    private static final int BLT_OUT_OF_RANGE = 1;
    private static final int BLT_NEVER_CONNECTED = 2;
    private static final int BLT_CONNECTED = 3;
    */

    //Timer to detect if Bluetooth is out of range
    private Timer timer = null;
    //Message queue for storing messages to be sent
    public List<String> sendLst = new ArrayList<>();
    private static final String TAG = "DFSBltCtl";
    private final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    /**
     * To start discovery
     */
    @Override
    public void run() {
        Log.d(TAG, "Bt controller start working.");
        // To get the single instance of MyApplication
        myApplication = MyApplication.getInstance();
        //To get local device Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the device supports Bluetooth and Bluetooth is not connected
        if (isBtSupported() && !isBltConnected()) {
            //update UI for StartActivity
            //sendHandlerMessage("Please wait for \nthe Bluetooth connection.",1003,mainActHandler);
            //Open bluetooth
            if (!isBtTurnedOn()) {turnOnBluetooth();}
            //Start discovery of Bluetooth devices nearby
            startDiscovery();
        }
    }

    /**
     * To get instance.
     * @return an unique instance of BluetoothController
     */
    public static BluetoothController getInstance(){
        if (bluetoothController == null) {
            bluetoothController = new BluetoothController();
        }
        return bluetoothController;
    }

    /**
     * Check if local device supports Bluetooth
     * @return boolean
     */
    private boolean isBtSupported(){
        boolean isBtSupported = (bluetoothAdapter != null);
        Log.d(TAG, "isBtSupported: "+isBtSupported);
        return isBtSupported;
    }

    /**
     * Check if Bluetooth is turned on.
     * @return boolean
     */
    private boolean isBtTurnedOn() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Turns on Bluetooth and wait until Bluetooth is on
     */
    private void turnOnBluetooth(){
        if (bluetoothAdapter != null){
            bluetoothAdapter.enable();
            while(true){
                if(isBtTurnedOn())break;
            }
            Log.d(TAG, "Turned on Bluetooth.");
        }
    }

    /**
     * Turns off the Bluetooth.
     */
    public void turnOffBluetooth() {
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled())
            bluetoothAdapter.disable();
        Log.d(TAG, "Turn off Bluetooth.");
    }

    /**
     *Test if the Bluetooth connection is fine.
     */
    public void testConnection(){
        //Test if the Bluetooth is activated.If not, activate it and reconnect to the device one more time
        if(!isBtTurnedOn()){
            turnOnBluetooth();
            sendHandlerMessage("Bluetooth has been disabled.\nPlease wait for the recover.",1002,dataActHandler);
            if(mDevice!=null) connectDevice(mDevice);
        }
        /*
          If the Bluetooth is activated, send a "connected" message to DEBA and build a timer.
          If the "connected" message has not been received in 2s, a toast will be shown
          to tell the user mobile phone out of range.
         */
        else{
            write("connected".getBytes());
            if (timer==null){
                /*
                *Tasks in this timer will be executed if timer counts down to the end.
                *It happens when Bluetooth connection is lost.
                */
                timer = new Timer();
                // Task to rebuild a socket
                final TimerTask rebuildSocket = new TimerTask() {
                    @Override
                    public void run() {
                        Log.d(TAG, "testConnection: timer task run.");
                        //Stop the connection and communication threads
                        mCommuThread.cancel();
                        mConnectThread.cancel();
                        //Update UI
                        sendHandlerMessage("Bluetooth out of range.Please get back to your seat!",1002,dataActHandler);
                        //Reconnect to the device
                        if(mDevice!=null) connectDevice(mDevice);
                        Log.d(TAG, "Bluetooth out of range. Rebuild socket.");
                    }
                };
                // Task to remind user of Bluetooth out of range
                final TimerTask notice = new TimerTask() {
                    @Override
                    public void run() {
                        sendHandlerMessage("Bluetooth out of range.",1002,dataActHandler);
                    }
                };
                // Task "rebuildSocket" will be executed once after 2s.
                timer.schedule(rebuildSocket,2000);
                // Task "notice" will be executed after 8s with a period of 8s.
                timer.schedule(notice,8000,8000);
            }
        }
    }

    /**
     * Make a salt of 64 bytes for the Bluetooth encryption.
     * @return a String
     */
    private String getSalt(){
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[64];
        random.nextBytes(salt);
        return new String(salt);
    }

    /**
     * Encrypt the password with the salt using "SHA-512" code.
     * @param password a String of a password
     * @param salt a String of a salt
     * @return a String of the encrypted password
     */
    private String getSecurePassword(String password, String salt){
        String securePassword = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            //encrypt salt
            md.update(salt.getBytes(UTF8_CHARSET));
            //encrypt password
            byte[] bytes = md.digest(password.getBytes(UTF8_CHARSET));
            //get the final encrypted password
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
            }
            securePassword = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.d(TAG, "getSecurePassword: "+e.toString());
        }
        return securePassword;
    }

    /**
     * Check if the client socket has been connected.
     * Attention: If the socket is connected now is not sure.
     * @return a boolean
     */
    public boolean isBltConnected(){
        return mClientSocket!=null && mClientSocket.isConnected();
    }

    /**
     * Start discovering the Bluetooth device around the phone
     */
    private void startDiscovery(){
        if (bluetoothAdapter != null && !bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        if (bluetoothAdapter != null){
            bluetoothAdapter.startDiscovery();
            Log.d(TAG,"start discovery");
        }
    }

    /**
     * Try to send message to DEBA
     * @param out byte[] containing the message
     */
    private void write(byte[] out){
        if (mCommuThread != null) {
            mCommuThread.write(out);
        }
        else{
            Log.d(TAG, "write: mCommuThread is null.");
        }
    }

    /**
     * Update UI
     * @param string a String of a message
     * @param what an int representing one situation
     * @param handler a Handler that handle the message
     */
    public void sendHandlerMessage(String string,int what,Handler handler){
        if(handler != null){
            Message msg = handler.obtainMessage(what);
            Bundle mBundle = new Bundle();
            mBundle.putString(CollectDataActivity.SHOW_MESSAGE,string);
            msg.setData(mBundle);
            msg.setTarget(handler);
            handler.sendMessage(msg);
        }
    }


    /**
     * a sub-class extended from BroadcastReceiver
     */
    class BluetoothReceiver extends BroadcastReceiver {
        /**
         * This function will be called if an intent is received
         * @param context the context the receiver is running
         * @param intent the intent received
         */
        @Override
        public void onReceive(Context context, Intent intent){
            String action=intent.getAction();
            if (action != null) {
                switch (action){
                    // A Bluetooth device is founded
                    case BluetoothDevice.ACTION_FOUND:
                        // See if the device is our target device
                        BluetoothDevice scanDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if(scanDevice == null || scanDevice.getName() == null) {
                            return;
                        }
                        String name = scanDevice.getName();
                        Log.d(TAG,scanDevice.getName());
                        if(name != null && name.equals(BLUETOOTH_NAME)){
                            Log.d(TAG,"Device found : "+name);
                            bluetoothAdapter.cancelDiscovery();
                            //Connect to this device
                            connectDevice(scanDevice);
                        }
                        break;

                    // The discovery is finished
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        //sendBtHandlerMessage("Discovery finished",1005);
                        Log.d(TAG,"Discovery finished");
                        // If target device hasn't been founded, restart the discovery
                        if (mDevice == null) bluetoothAdapter.startDiscovery();
                        break;
                }
            }
        }
    }

    /**
     * get an instance of BluetoothReceiver
     * @return the unique BluetoothReceiver in this controller
     */
    private BroadcastReceiver getBroadcastReceiver(){
        if (btReceiver == null){
            btReceiver = new BluetoothReceiver();
        }
        return btReceiver;
    }

    /**
     * register the receiver
     * @param context the context where the receiver is registered.
     */
    public void registerBtReceiver(Context context){
        IntentFilter intentFilter = new IntentFilter();
        //Only receive these two intents
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(getBroadcastReceiver(),intentFilter);
        Log.d(TAG, "Registered bt receiver.");
    }

    /**
     * unregister the receiver
     * @param context the context where the receiver is unregistered.
     */
    public void unregisterBtReceiver(Context context){
        context.unregisterReceiver(getBroadcastReceiver());
        Log.d(TAG, "Unregistered bt Receiver.");
    }

    /**
     * Connect to the device
     * @param device a BluetoothDevice
     */
    private synchronized void connectDevice(BluetoothDevice device) {
        Log.d(TAG, "connectDevice");
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        //If the device has not been bonded, bond it
        if (device.getBondState() != BOND_BONDED ){
            try { // create Bond
                Method method = BluetoothDevice.class.getMethod("createBond");
                method.invoke(device);
                Log.d(TAG, "created bond with target device.");
            } catch (Exception e) {
                Log.e(TAG,"Bond failed");
            }
        }
        //start a connection thread
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    /**
     * Start the communication task
     * @param socket a BluetoothSocket on which the Bluetooth connection is established
     */
    private synchronized void communicate(BluetoothSocket socket) {
        if(mCommuThread!=null){
            mCommuThread.cancel();
            mCommuThread = null;
        }
        mCommuThread = new CommunicationThread(socket);
        mCommuThread.setDaemon(true);
        mCommuThread.start();
    }

    /**
     * This class takes care of the establishment of the connection in a thread
     */
    public class ConnectThread extends Thread {
        /**
         * Constructor of the class
         * @param device the BluetoothDevice to be connected
         */
        ConnectThread(BluetoothDevice device) {
            mDevice = device;
        }

        /**
         * Connect to device
         */
        @Override
        public void run(){
            Log.d(TAG, "mConnectThread starts.");
            try {
                Method m = mDevice.getClass().getMethod("createRfcommSocket", int.class);
                mClientSocket = (BluetoothSocket) m.invoke(mDevice, 1);
                mClientSocket.connect();
                Log.d(TAG, "run: called communicate() function.");
                //Communication
                communicate(mClientSocket);
            } catch (Exception e1) {
                Log.e(TAG,"run: "+e1.toString());
                connectDevice(mDevice);
            }
            Log.d(TAG, "mConnectThread finished.");
        }

        /**
         * stop the connection thread
         */
        private void cancel(){
            try{
                mClientSocket.close();
            }
            catch (IOException e){
                Log.e(TAG, "Could not close the client socket", e);
                Log.e(TAG,"socket cancellation failed");
            }
        }

    }

    /**
     *This class is responsible of the communication between DMA and DEBA in form of a thread
     */
    public class CommunicationThread extends Thread{

        private InputStream mInStream;
        private OutputStream mOutStream;
        //The socket for the communication
        private BluetoothSocket mCommuSocket;
        private boolean isRead;
        //a list storing the messages received before handling them
        private List<String> messageList = new ArrayList<>();

        /**
         * Constructor of the class
         * @param socket a connected BluetoothSocket
         */
        CommunicationThread(BluetoothSocket socket){
            mCommuSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            //To get input stream and output stream
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error :", e);
            }
            if (tmpIn != null) {
                mInStream = tmpIn;
            }
            mOutStream = tmpOut;
        }

        /**
         * Deal with the received data
         */
        @Override
        public void run(){
            Log.d(TAG,"Bluetooth Communication Started.");
            // Check the connection
            testConnection();
            //synchronize time
            syncTime();
            // if handler is null, no UI update(one of them (mainActHandler and dataActHandler) is null)
            sendHandlerMessage("",1008,mainActHandler);
            sendHandlerMessage("Bluetooth connection recovered.",1002,dataActHandler);

            isRead = true;
            while (isRead) {
                // read the input stream
                String msg = read();
                if (msg != null){
                    //receiving "connected" means a good connection
                    if (msg.contains("connected")){
                        //cancel the timer
                        timer.cancel();
                        timer=null;
                        //send all the messages in the list sendLst
                        while(true){
                            if(!sendLst.isEmpty()){
                                for (;!sendLst.isEmpty();){
                                    write(sendLst.remove(0).getBytes());
                                }
                                break;
                            }
                        }
                    }
                    // The plane has took off
                    if (msg.contains("is_took-off")){
                        myApplication.setIsTookOff(true);
                        sendHandlerMessage("The plane has taken off.",1008,dataActHandler);
                        Log.d(TAG, "The plane has taken off." );
                    }
                    //The plane has landed
                    if (msg.contains("is_landed")){
                        myApplication.setIsLanded(true);
                        sendHandlerMessage("The plane has landed.",1009,dataActHandler);
                        Log.d(TAG, "The plane has landed." );
                    }
                    // flight data has been sent
                    if (msg.contains("is_flight_data_sent")){
                        myApplication.setIsFlightDataSent(true);
                        sendHandlerMessage("Flight data has been sent.",1002,dataActHandler);
                        sendHandlerMessage("",1011,dataActHandler);
                        Log.d(TAG, "Send flight data successfully.");
                    }
                    //lack of flight data
                    if (msg.contains("lack of flight data")){
                        sendHandlerMessage("Please help us enter the flight data.\nThank you.",1002,dataActHandler);
                        sendHandlerMessage("",1003,dataActHandler);
                    }
                    //receiving the log file
                    if (msg.contains("file")){
                        String fileName = msg.substring(5);
                        receiveFile(fileName);
                    }
                }
            }
        }

        /**
         * Extract a byte[] by a given length
         * @param b the former byte[]
         * @param len length of byte needed
         * @return a sub array of byte
         */
        private byte[] getContent(byte[] b, int len){
            byte[] sb = new byte[len];
            System.arraycopy(b, 0, sb, 0, len);
            return sb;
        }

        /**
         * Read the data from the input stream mInStream
         * @return a String
         */
        private String read(){
            // put message in to a message list and return the first element
            String msg;
            int len;
            byte[] buffer = new byte[1024];
            try {
                if ((len = mInStream.read(buffer)) != -1) {
                    byte[] subBuffer = new byte[len];
                    System.arraycopy(buffer, 0, subBuffer, 0, len);
                    msg = new String(subBuffer, "UTF-8");
                    messageList.add(msg);
                    Log.d(TAG, "receive data:" + msg);
                }
                return messageList.remove(0);
            } catch (IOException e) {
                return null;
            }
        }

        /**
         * close the input stream and the output strean and the bluetooth socket
         */
        private void cancel() {
            try {
                mCommuSocket.close();
            } catch (IOException e ) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
            try{
                mInStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the input stream", e);
            }
            try{
                mOutStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the output stream", e);
            }
        }

        /**
         * Write an array of bytes into the output stream
         * @param buffer an array of bytes
         */
        private void write(byte[] buffer){
            int len = buffer.length;
            try {
                mOutStream.write(buffer,0,len);
                Log.d(TAG, "write: "+new String(buffer,"UTF-8"));
            } catch (IOException e) {
                sendHandlerMessage("Bluetooth out of range",1002,dataActHandler);
                Log.e(TAG,"Write failed: " + e.toString());
            }
        }

        /**
         * Receive the content of a file and put them into a list of Daemon lst_content.
         * This class cannot do not have the right to write a file.
         * @param fileName a String of the file name
         */
        private void receiveFile(String fileName){
            DaemonService.LOG_FILE_NAME = fileName;
            Log.d(TAG, "receiveFile: file name is " + fileName);
            sendHandlerMessage("Receiving file:" + fileName, 1004,dataActHandler);
            byte[] b = new byte[1024];
            int len;
            try{
                while (-1 != (len = mInStream.read(b))) {
                    DaemonService.lst_content.add(getContent(b,len));
                }
            }catch (IOException e){
                Log.e(TAG, "run: InputStream read failed: "+e.toString());
            }
        }

        /**
         * Send the current utc time to Raspberry Pi to synchronize the time on RPi
         */
        private void syncTime(){
            try {
                String str = new JSONStringer().object()
                        .key("type").value(3)
                        .key("utcTime").value((System.currentTimeMillis())/1000)
                        .endObject().toString();
                sendLst.add(str);
                Log.d(TAG, "Send utc time to rpi for synchronization.");
            } catch (JSONException e) {
                Log.d(TAG, "Time synchronization failed: ",e);
            }
        }
    }
}
