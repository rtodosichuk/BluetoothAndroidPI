package com.example.rtodosichuk.bluetoothpi;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    Button btnScan;
    ListView listView;
    ArrayAdapter<String> listAdapter;
    Set<BluetoothDevice> pairedDevices;
    BluetoothDevice pairedDevice;
    BluetoothAdapter btAdapter;

    //private static final UUID MY_UUID = UUID.fromString("0000110e-0000-1000-8000-00805f9b34fb");
    //private static final UUID MY_UUID = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb");

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        getPairedDevices();
    }

    private void getPairedDevices() {
        pairedDevices = btAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice bt : pairedDevices) {
                pairedDevice = bt;
                ParcelUuid[] pu = bt.getUuids();
                listAdapter.add("Name:" + bt.getName()+"\n"
                        + "Address:"+bt.getAddress()+"\n"
//                        + "State:"+bt.getBondState()+"\n"
//                        + "UUID:"+ pu[1]
                );
            }
            Toast.makeText(getApplicationContext(), "Showing Paired Devices", Toast.LENGTH_SHORT).show();
        }
    }

    private void init() {
        btnScan = (Button)findViewById(R.id.btnScan);
        listView = (ListView)findViewById(R.id.listView);
        listView.setOnItemClickListener(this);
        listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, 0);
        listView.setAdapter(listAdapter);
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter == null) {
            Toast.makeText(getApplicationContext(), "No bluetooth detected", Toast.LENGTH_SHORT).show();
            finish();
        }
        else {
            if (!btAdapter.isEnabled()) {
                turnOnBluetooth();
            }
        }
    }

    private void turnOnBluetooth() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, 1);
    }

    public void scanClicked(View v) {
        getPairedDevices();
        Toast.makeText(getApplicationContext(), "Scanning for paired bluetooth devices", Toast.LENGTH_LONG).show();
    }

    public void connectBT(View v) {
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }

        if (pairedDevice != null) {
            ConnectThread connect = new ConnectThread(pairedDevice);
            connect.start();
        }
    }

    public void sendOne(View v) {
        if (pairedDevice != null && mSocket != null) {
            mHandler.obtainMessage(MESSAGE_WRITE, "1").sendToTarget();
        }
    }

    public void sendZero(View v) {
        if (pairedDevice != null && mSocket != null) {
            mHandler.obtainMessage(MESSAGE_WRITE, "0").sendToTarget();
        }
    }

    public void sendQuit(View v) {
        if (pairedDevice != null && mSocket != null) {
            mHandler.obtainMessage(MESSAGE_WRITE, "q").sendToTarget();
        }
    }

    public void sendText(View v) {
        if (pairedDevice != null && mSocket != null) {
            mHandler.obtainMessage(MESSAGE_WRITE, "Hello World!!!").sendToTarget();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Toast.makeText(getApplicationContext(), "Clicked List view item", Toast.LENGTH_SHORT).show();
    }


    /*********/
    /*Threads*/
    /*********/
    public static final int SUCCESS_CONNECT = 7;
    public static final int MESSAGE_READ = 0;
    public static final int MESSAGE_WRITE = 1;
    public static final int MESSAGE_TOAST = 2;
    BluetoothSocket mSocket;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case SUCCESS_CONNECT:
                    mSocket = (BluetoothSocket) msg.obj;
                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_WRITE:
                    if (mSocket != null) {
                        ConnectedThread sendThread = new ConnectedThread(mSocket);
                        sendThread.start();
                        String sMsg = (String) msg.obj;
                        sendThread.write(sMsg.getBytes());
                        Toast.makeText(getApplicationContext(), "Sending: " + sMsg, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[])msg.obj;
                    String sRead = new String(readBuf);
                    Toast.makeText(getApplicationContext(), sRead, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                //MY_UUID is the app's UUID string, also used in the server code.
                //tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
                //tmp = mmDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                Method method = device.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
                tmp = (BluetoothSocket) method.invoke(device, 1);

//            } catch (IOException e) {
//                Log.e(TAG, "Socket's create() method failed", e);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            btAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                if (!mmSocket.isConnected()) {
                    mmSocket.connect();
                }
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                Log.e(TAG, "socket connect error", connectException);
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            //manageMyConnectedSocket(mmSocket);

            mHandler.obtainMessage(SUCCESS_CONNECT, mmSocket).sendToTarget();
        }

        //private void manageMyConnectedSocket(BluetoothSocket mmSocket) {

        //}

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            int numBytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    mmBuffer = new byte[1024];
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    Message readMsg = mHandler.obtainMessage(
                            MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget();
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
            //    Message writtenMsg = mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, mmBuffer);
            //    writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg = mHandler.obtainMessage(MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast", "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

}
