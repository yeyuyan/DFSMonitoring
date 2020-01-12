package com.example.yye.dfsmonitoring;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;


import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;


public class ExternalCommuController extends Thread{
    private static final String TAG = "DFSExtCommuCtl";
    // host name
    private static final String hostName = "192.168.100.105";
    //url address of server for the uploading
    private static final String urlStr = "https://"+hostName+":6578";
    //name of certificate stored in assets directory
    private static final String crtName = "domain.cer";
    //file name to be uploaded
    private static final String fileName="Log_data.json";

    private boolean isConnected;
    private Context mContext;
    public static Handler mainActHandler;
    public static Handler dataActHandler;
    private MyApplication myApplication;

    /**
     * Constructor of ExternalCommuController
     * @param context Context must be passed as a param
     *                because the class is a thread which is static
     */
    ExternalCommuController(Context context){
        mContext = context;
        myApplication = MyApplication.getInstance();
    }

    /**
     * Function to be called when the thread executes.
     * Firstly waits for the reception of the log file
     * Secondly checks if there is a network available
     * if no, wait for the good network.
     * Thirdly tries to establish a https connection
     * if connection is established, uploads the log file.
     */
    public void run(){
        Log.d(TAG, "run: start External Communication Controller.");
        Log.d(TAG, "run: Wait for the writing of log file");
        //Firstly waits for the reception of the log file
        JSONObject jsonObj;
        String jsonStr;
        while (true){
            if (myApplication.isLogfileWritten()){
                //cancel the notification of lack of data in CollectDataActivity
                sendHandlerMessage("",1007,dataActHandler);
                //tell user log file is received.
                sendHandlerMessage("The log file received! Now you can unplug the device.",1004,dataActHandler);
                try {
                    //loads json file to a json string
                    jsonStr = fileToString(myApplication.getPath()+myApplication.getPkgName()+fileName);
                    //builds a json object
                    jsonObj = new JSONObject(jsonStr);
                    Log.d(TAG, "run: "+jsonObj.toString());
                    Log.d(TAG, "run: log file is ready.");
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    jsonStr = null;
                    break;
                }
            }
        }

        //Secondly wait for a good network connection.
        waitForNetwork();

        // Thirdly try to establish the https connection and upload file.
        try{
            //Loads s Certificate.
            Certificate ca = loadCertificate(crtName);
            //Creates a Keystore storing the certificate.
            KeyStore keyStore = createKeyStore(ca);
            //Creates a TrustManagerFactory trusting certificates in the keystore.
            TrustManagerFactory trustManagerFactory = createTrustManagerFactory(keyStore);
            //Using our TrustManagerFactory to create a SSLContext.
            SSLContext sslContext = createSSLContext(trustManagerFactory);
            //establish a connection
            HttpsURLConnection connection = connectToServer(sslContext);
            //If connected
            if (connection != null && isConnected){
                Log.d(TAG,"run: Start uploading.");
                sendHandlerMessage("Uploading log file.",1004,dataActHandler);
                //upload the log file
                try {
                    if (uploadLogfile(addSlashes(jsonStr),urlStr,connection)){
                    //if (uploadLogfile(addSlashes(jsonObj.toString()),urlStr,connection)){
                        sendHandlerMessage("Success! ",1004,dataActHandler);
                    } else sendHandlerMessage("upload failed",1004,dataActHandler);
                } catch (Exception e) {
                    Log.d(TAG, "Log file failed to send to server. " + e.toString());
                }
                //Disconnects to the server whatever the result.
                connection.disconnect();
                SystemClock.sleep(1000);
                sendHandlerMessage("",1005,dataActHandler);
            }
        }catch (Exception e){
            Log.d(TAG, "Exception:"+e);
            sendHandlerMessage("",1005,dataActHandler);
        }
        Log.d(TAG, "run: External communication controller stops.");
    }


    /**
     *Checks if there is a network available. If no, wait for the network.
     */
    private void waitForNetwork(){
        if(!isNetworkAvailable(mContext)) {
            sendHandlerMessage("", 1006,dataActHandler);
            sendHandlerMessage("Network is unavailable",1004,dataActHandler);
            Log.d(TAG, "run: wait for the network.");
            while (true) {
                if (isNetworkAvailable(mContext)) {
                    sendHandlerMessage("", 1007,dataActHandler);
                    sendHandlerMessage("Network is available now",1004,dataActHandler);
                    Log.d(TAG, "run: Network is available");
                    break;
                }
            }
        }
    }

    /**
     * Try to connect to server.
     * @param sslContext Context containing socket factories
     * @return If connection is established,return a HttpsURLConnection, else return null.
     */
    private HttpsURLConnection connectToServer(SSLContext sslContext){
        HttpsURLConnection connection = null;
        //To verify the host name
        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return hostname.equals(hostName);
            }
        };
        try{
            URL url = new URL(urlStr);
            connection = (HttpsURLConnection)url.openConnection();
            connection.setHostnameVerifier(hostnameVerifier);
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setConnectTimeout(5*1000);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-type","application/json; charset=UTF-8");
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Connection","keep-Alive");
            String basicAuth = "Basic " + new String(Base64.encode("ok:ok".getBytes(),Base64.DEFAULT));
            connection.addRequestProperty("Authorization", basicAuth);
            connection.connect();
            isConnected = true;
            Log.d(TAG, "connectToServer: success!");
        }catch(Exception e){
            isConnected = false;
            sendHandlerMessage("",1005,dataActHandler);
            sendHandlerMessage("Failed to connect to server.",1002,dataActHandler);
            Log.e(TAG, "connectToServer: ",e);
        }
        return connection;
    }

    /**
     * Uploads log file to server
     * @param jsonStr a json string containing all the data
     * @param urlStr url address for uploading
     * @param connection a Https Connection
     * @return a boolean represented the upload result
     * @throws Exception IOException
     */
    private boolean uploadLogfile(String jsonStr,String urlStr,HttpsURLConnection connection)throws Exception{
        if(jsonStr == null)return false;
        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
        writer.write(jsonStr);
        writer.flush();
        writer.close();

        // to get the answer for POST
        int code = connection.getResponseCode();
        Log.d(TAG, "uploadLogfile: code="+code+" url="+urlStr);
        if (code == 200){
            Log.d(TAG, "uploadLogfile: Log file sent to server successfully.");
            //To get input from the server
            InputStream ins = connection.getInputStream();
            int chr;
            StringBuilder b = new StringBuilder();
            while ((chr = ins.read()) != -1) {
                b.append((char) chr);
            }
            Log.d(TAG, "uploadLogfile: message from server: " + b.toString());
            return true;
        }
        return false;
    }

    /**
     * Checks if network available
     * @param context the Context on which a connectivity service is running
     * @return a boolean telling the result.
     */
    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm != null && cm.getActiveNetworkInfo()!= null && cm.getActiveNetworkInfo().isConnected();
    }

    /**
     * Send message to handler.
     * @param string a String put to the message.
     * @param what a message code to identify the type of message
     * @param handler the target Handler
     */
    private void sendHandlerMessage(String string,int what,Handler handler){
        if (handler!=null){
            Message msg = handler.obtainMessage(what);
            Bundle mBundle = new Bundle();
            mBundle.putString(CollectDataActivity.SHOW_MESSAGE,string);
            msg.setData(mBundle);
            handler.sendMessage(msg);
        }
    }

    /**
     * Loads CAs from an InputStream got from a crt file
     * @param crtName name of the crt file
     * @return a Certificate
     * @throws Exception a Certificate Exception
     */
    private Certificate loadCertificate(String crtName)throws Exception{
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream caInput = mContext.getAssets().open(crtName);
        Certificate ca;
        try{
            ca = cf.generateCertificate(caInput);
            Log.d(TAG, "loadCertificate: ca = "+((X509Certificate) ca).getSubjectDN());
        }finally {
            caInput.close();
        }
        return ca;
    }

    /**
     * Creates a KeyStore containing our trusted CAs
     * @param ca a certificate to be added
     * @return a Keystore
     * @throws Exception may have a IOExeption or a KeyStoreException
     */
    private KeyStore createKeyStore(Certificate ca)throws Exception{
        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        //Loads the pfx file in the Assets directory
        keyStore.load(mContext.getAssets().open("domain.pfx"),"".toCharArray());
        keyStore.setCertificateEntry("ca",ca);
        return keyStore;
    }

    /**
     * Creates a TrustManager that trusts the CAs in our KeyStore
     * @param keyStore a KeyStore containing trusted CAs
     * @return a TrustManagerFactory
     * @throws Exception may have a KeyStoreException or a NoSuchAlgorithmException
     */
    private TrustManagerFactory createTrustManagerFactory (KeyStore keyStore) throws Exception{
        String tmAlgo = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(tmAlgo);
        trustManagerFactory.init(keyStore);
        return trustManagerFactory;
    }

    /**
     * Creates an SSLContext that uses our TrustManagers.
     * @param trustManagerFactory a TrustManagerFactory containing all TrustManagers.
     * @return a SSlContext
     * @throws Exception may have a NoSuchAlgorithmException or a KeyManagementException
     */
    private SSLContext  createSSLContext(TrustManagerFactory trustManagerFactory)throws Exception{
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null,trustManagerFactory.getTrustManagers(),null);
        return sslContext;
    }

    /**
     * Extract a String from a file
     * @param fileName the file name needs to be extracted
     * @return a string
     * @throws Exception may have a IOException
     */
    private String fileToString(String fileName) throws Exception{
        File file = new File(fileName);
        FileInputStream fins = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer=new byte[1024];
        int len;
        while( (len = fins.read(buffer)) != -1)
        {
            bos.write(buffer,0,len);
        }
        bos.flush();
        bos.close();
        fins.close();
        return bos.toString();
    }

    /**
     * replace " with /" in a json string
     * @param jsonStr a String needs to be changed to adapt the php form
     * @return a String
     */
    private String addSlashes(String jsonStr){
        if (jsonStr!=null && jsonStr.contains("\"")){return jsonStr.replace("\"","\\\"");}
        else return jsonStr;
    }

}
