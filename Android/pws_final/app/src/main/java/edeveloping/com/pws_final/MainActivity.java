package edeveloping.com.pws_final;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.Timer;
import java.util.TimerTask;

import socket.IOAcknowledge;
import socket.IOCallback;
import socket.SocketIO;
import socket.SocketIOException;


public class MainActivity extends ActionBarActivity implements SensorEventListener {

    final String portServer = "3000";

    SocketIO mSocket;

    Button btnStart;
    Button btnSetIp;
    Button btnChangeValues;
    Button btnMoveRobot;
    EditText etIpAdress, etTau, etKp;

    RelativeLayout layoutIpSelect, layoutControls;

    String ipAdress;

    float tau, kp;

    boolean isStarted = false;

    //vars for orientationvector
    private SensorManager mSensorManager;
    private WindowManager mWindowManager;
        private float[] mAccelData = new float[3]; //om accel. data in op te slaan
        private float[] mGyroData = new float[3]; //om gyro data in op te slaan
        private float[] mGeomagneticData = new float[3]; //om mag. data in op te slaan
        private float[] gemOrientatie = new float[3];
        private boolean OrientationIsGemiddeld = false;

    //for reporting to the raspberry
    int hitsPersSecond = 10;
    int interval = 1000/hitsPersSecond;
    Timer timerForComms;

    final Boolean DEBUG = true , OUTPUT_SENSORS = false;
    final String TAG = "PWS_Main_Activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = (Button) findViewById(R.id.btnStart);
        btnSetIp = (Button) findViewById(R.id.btnConnect);
        btnChangeValues = (Button) findViewById(R.id.btnUpdateValues);
        btnMoveRobot = (Button) findViewById(R.id.btnSetSetpoint);
        etIpAdress = (EditText) findViewById(R.id.etIpAdress);
        etTau = (EditText) findViewById(R.id.etTau);
        etKp = (EditText) findViewById(R.id.etKp);
        layoutIpSelect = (RelativeLayout) findViewById(R.id.ipSelectLayout);
        layoutControls = (RelativeLayout) findViewById(R.id.controlsLayout);

        layoutControls.setVisibility(View.GONE);

        btnStart.setText("Start");
        btnStart.setBackgroundColor(Color.GREEN);

        mSensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        mWindowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);

        btnSetIp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    ipAdress = etIpAdress.getText().toString();

                    Toast.makeText(getApplicationContext(), "Ip adress : " + ipAdress, Toast.LENGTH_SHORT).show();

                    mSocket = new SocketIO("http://" + ipAdress + ":" + portServer+"", mSocketCallback);


                }catch (MalformedURLException e){
                    e.printStackTrace();
                }

            }
        });


        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if (isStarted) {
                        btnStart.setText("Start");
                        btnStart.setBackgroundColor(Color.GREEN);

                        isStarted = false;

                        JSONObject o = new JSONObject();
                        o.put("startCode", 0);
                        //TODO
                        o.put("tau", 0);
                        o.put("kp", 0);

                        mSocket.emit("start", o);

                    } else {
                        btnStart.setText("Stop");
                        btnStart.setBackgroundColor(Color.RED);

                        isStarted = true;

                        tau = Float.valueOf(etTau.getText().toString());
                        kp = Float.valueOf(etKp.getText().toString());

                        JSONObject o = new JSONObject();
                        o.put("startCode", 1);
                        //TODO
                        o.put("tau", tau*10);
                        o.put("kp", kp*10);

                        mSocket.emit("start", o);

                    }
                }catch (JSONException e){
                    e.printStackTrace();
                }
            }
        });

        btnMoveRobot.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                try {
                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            //set setpoint as angle
                            //first calibrate
                            //then start timer

                            calibrateSensors();


                            if(timerForComms == null) {
                                timerForComms = new Timer();
                                timerForComms.scheduleAtFixedRate(geefOrientatieAanRaspberry, 0, interval);
                            }


                            break;

                        case MotionEvent.ACTION_UP:
                            //set setpoint to 0
                            OrientationIsGemiddeld = false;

                            JSONObject o = new JSONObject();
                            o.put("deltaY", "0");
                            mSocket.emit("control", o);
                            break;
                    }
                }catch (JSONException e){
                    e.printStackTrace();
                }

                return true;
            }
        });

        btnChangeValues.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isStarted){
                    try {
                        tau = Float.valueOf(etTau.getText().toString());
                        kp = Float.valueOf(etKp.getText().toString());

                        JSONObject o = new JSONObject();
                        o.put("startCode", 1);
                        //TODO
                        o.put("tau", tau * 10);
                        o.put("kp", kp * 10);

                        mSocket.emit("start", o);
                    }catch (JSONException e){
                        e.printStackTrace();
                    }
                }
            }
        });

        //register all the sensors we need
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME );
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME );
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) , SensorManager.SENSOR_DELAY_GAME);

    }

    @Override
    public void onStop() {
        super.onStop();
        //stop everything

        timerForComms.cancel();
        mSocket.disconnect();
        mSensorManager.unregisterListener(this);
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    /**
     * CALLBACK FOR SOCKET IO
     * */

    IOCallback mSocketCallback = new IOCallback() {
        @Override
        public void onDisconnect() {
            System.out.println("Connection terminated.");
        }

        @Override
        public void onConnect() {
            System.out.println("Connection established");

            //make controlls visible
            layoutControls.post(new Runnable() {
                @Override
                public void run() {
                    layoutControls.setVisibility(View.VISIBLE);
                }
            });

            layoutIpSelect.post(new Runnable() {
                @Override
                public void run() {
                    layoutIpSelect.setVisibility(View.GONE);
                }
            });
        }


        @Override
        public void onMessage(String data, IOAcknowledge ack) {
            System.out.println("Server said: " + data);
        }

        @Override
        public void onMessage(JSONObject json, IOAcknowledge ack) {
            // try {
            System.out.println("Server said:" + json.toString());
            //  } catch (JSONException e) {
            //      e.printStackTrace();
            //  }
        }

        @Override
        public void on(String event, IOAcknowledge ack, Object... args) {
            System.out.println("Server triggered event '" + event + "'");
            System.out.println(args.length);
        }

        @Override
        public void onError(SocketIOException socketIOException) {
            System.out.println("an Error occured");
            socketIOException.printStackTrace();
        }
    };

    public void calibrateSensors(){

        float R[] = new float[9];
        float I[] = new float[9];
        boolean success = SensorManager.getRotationMatrix(R, I, mAccelData, mGeomagneticData);
        if (success) {
            float orientation[] = new float[3];
            SensorManager.getOrientation(R, orientation);
            gemOrientatie[0] = orientation[0] * (float) (180 / Math.PI); //verander van RAD, naar DEG
            gemOrientatie[1] = orientation[1] * (float) (180 / Math.PI);
            gemOrientatie[2] = orientation[2] * (float) (180 / Math.PI);

            OrientationIsGemiddeld = true;
            if(DEBUG){
                Log.d(TAG, "gem: X:"+gemOrientatie[1]+" / Y:"+gemOrientatie[2]+" / Z:"+gemOrientatie[0]);
            }
        }

    }

    /**
     * giving the orientation vector to the raspberry
     * */

    private TimerTask geefOrientatieAanRaspberry = new TimerTask() {
        public void run() {
            try {
                float R[] = new float[9];
                float I[] = new float[9];
                boolean success = SensorManager.getRotationMatrix(R, I, mAccelData, mGeomagneticData);

                if (success && OrientationIsGemiddeld) {
                    float orientation[] = new float[3];
                    SensorManager.getOrientation(R, orientation);

                    float deltaX = 0.0f, deltaY = 0.0f, deltaZ = 0.0f;

                    deltaZ = (orientation[0] * (float) (180 / Math.PI)) - gemOrientatie[0];
                    deltaX = (orientation[1] * (float) (180 / Math.PI)) - gemOrientatie[1];
                    deltaY = (orientation[2] * (float) (180 / Math.PI)) - gemOrientatie[2];

                    if (DEBUG) {
                        Log.d(TAG, "deltaZ :" + deltaZ + " / deltaX : " + deltaX + " / deltaY : " + deltaY);
                    }

                    if (mSocket.isConnected()) {

                        JSONObject o = new JSONObject();

                        o.put("deltaY", deltaX);

                        mSocket.emit("control", o);
                    }

                }
            }catch (JSONException e){
                e.printStackTrace();
            }
        }
    };

    /**
     * get the sensor values from the sensor
     * */

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return;
        }

        //process the data, and put it in the right Array
        loadNewSensorData(event);
    }

    /**
     * put the sensor data in the right Array, nadat het gemiddelt is
     * */


    private void loadNewSensorData(SensorEvent event) {
        final int type = event.sensor.getType();

        if(type == Sensor.TYPE_GYROSCOPE){
            //if sensor is unreliable, return void
            if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
            {
                return;
            }
            mGyroData[0]=(mGyroData[0]*2+event.values[0])*0.33334f; // je had er 3, nu weer 1.(middelen)
            mGyroData[1]=(mGyroData[1]*2+event.values[1])*0.33334f;
            mGyroData[2]=(mGyroData[2]*2+event.values[2])*0.33334f;
            //else it will output the Roll, Pitch and Yawn values

            if(DEBUG && OUTPUT_SENSORS){
                Log.d(TAG, "Gyro : "+"\n"+
                        " X : "+ Float.toString(event.values[0]) +"\n"+
                        " Y : "+ Float.toString(event.values[1]) +"\n"+
                        " Z : "+ Float.toString(event.values[2]));
            }
        }

        if (type == Sensor.TYPE_ACCELEROMETER) {
            //Smoothing the sensor data a bit
            mAccelData[0]=(mAccelData[0]*2+event.values[0])*0.33334f;// je had er 3, nu weer 1. (middelen)
            mAccelData[1]=(mAccelData[1]*2+event.values[1])*0.33334f;
            mAccelData[2]=(mAccelData[2]*2+event.values[2])*0.33334f;

            if(DEBUG && OUTPUT_SENSORS){
                Log.d(TAG, "Accel : "+"\n"+
                        " X : "+ Float.toString(event.values[0]) +"\n"+
                        " Y : "+ Float.toString(event.values[1]) +"\n"+
                        " Z : "+ Float.toString(event.values[2]));
            }

        }

        if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            //Smoothing the sensor data a bit
            mGeomagneticData[0]=(mGeomagneticData[0]*1+event.values[0])*0.5f;
            mGeomagneticData[1]=(mGeomagneticData[1]*1+event.values[1])*0.5f;
            mGeomagneticData[2]=(mGeomagneticData[2]*1+event.values[2])*0.5f;

            float x = mGeomagneticData[0];
            float y = mGeomagneticData[1];
            float z = mGeomagneticData[2];
            double field = Math.sqrt(x*x+y*y+z*z);
            if (field>25 && field<65){
                // Log.e(TAG, "loadNewSensorData : wrong magnetic data, need a recalibration field = " + field);
            }

            if(DEBUG && OUTPUT_SENSORS){
                Log.d(TAG, "MAG : "+"\n"+
                                " X : "+ Float.toString(x) +"\n"+
                                " Y : "+ Float.toString(y) +"\n"+
                                " Z : "+ Float.toString(z) +"\n"+
                                " Field: "+ Double.toString(field)
                );
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO implemend this in someway, or maybe not

        if(DEBUG){
            Log.d(TAG, "sensor : "+sensor.getName()+" : accuracy = " +accuracy );
        }

    }
}
