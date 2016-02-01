package com.example.cedar.grakontestapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.UUID;

/**
* This bit of code houses both the lighting control screen as well as the color picker screen.
* This class calls a BLE wrapper class to handle the connection and communication process with the micro-controller
* It also calls the ColorPicker class to create the color wheel and ring.
* The functions are explained as they appear
*/

public class Chat extends Activity {

	private final static int NUM_TOGGLE_BUTTONS = 4;
	private int RSSI_THRESHOLD = -50;
    private static final int NUM_MODES = 5;
	private TextView tv = null;
    private Dialog mDialog;
    private SeekBar overhead_seekbar;
    private SeekBar reading_seekbar;
	private int activeAmbientMode;
    private boolean inColorSeclector = false;
    private modeObj[] modes; // SAVE ME
    private ColorPicker colorPicker;
    private String[] AmbientModes = {"Relax","Night Drive","Custom 1"
            ,"Custom 2","Custom 3"}; // SAME ME
    private CustomOnItemSelectedListener myListener;
    private BleWrapper mBleWrapper = null;
    private int lastKnownRssi = 0;
    private TextView rssi_value = null;
    private int[] rssi_check = new int[]{0,0,0,0,0,0,0,0,0,0};
    private TextView connect_disconnect_textView;
    boolean inrange = false;
    boolean deviceConnected = false;
    boolean firstConnection = false;


    private boolean modeToggleButtons[];
    // Lamp States
    private int overhead_intensity;
    private int reading_lamp_intensity;

    private static final UUID
            UUID_RBL = UUID.fromString("713d0000-503e-4c75-ba94-3148f18d941e"),
            UUID_TX = UUID.fromString("713d0003-503e-4c75-ba94-3148f18d941e"),
            UUID_RX = UUID.fromString("713d0002-503e-4c75-ba94-3148f18d941e");


    PropertyChangeListener listener = new PropertyChangeListener() { // Listener
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            assert "color".equals(evt.getPropertyName());
            boolean oldColor = (Boolean) evt.getOldValue();
            boolean newColor = (Boolean) evt.getNewValue();

            Toast.makeText(getApplicationContext(), "Started tracking colorpicker: " + newColor , Toast.LENGTH_SHORT).show();
            //System.err.println(String.format("Property color updated: %b -> %b", oldColor, newColor));
        }
    };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.modes); // Sets the screen to mode screen
        //IMPORT CODE
        rssi_value = (TextView) findViewById(R.id.rssi_value);
        connect_disconnect_textView = (TextView) findViewById(R.id.connect_disconnect_textView);
        if(!deviceConnected){
            showRoundProcessDialog(Chat.this, R.layout.loading_process_dialog_anim);
        } //Code for the Loading Dialog

        mBleWrapper = new BleWrapper(this, new BleWrapperUiCallbacks.Null() // This function controls how the Bluetooth operates. It's constantly  running in the background.
        {
            @Override
            public void uiDeviceFound(final BluetoothDevice device,
                                      final int rssi,
                                      final byte[] record) // If  a device is found then function will call the Bluetooth Wrapper Class to connect
            {

                Log.d("GRAKON:", "" + rssi);
                setRSSI(rssi);

                if(device.getName().equals("BLE Shield"))
                {
                    boolean status;
                    status = mBleWrapper.connect(device.getAddress().toString());
                    if(!status)
                    {

                    }
                }

            }

            @Override
            public void uiDeviceConnected(final BluetoothGatt gatt,
                                          final BluetoothDevice device) // Once the device is completely connected the app will stop scanning and log the connection
            {
                String test = device.getName();
                Log.d("CONNECTED: ", "Connected to a device: " + test);
                deviceConnected = true; // local variable for class to access
                firstConnection = true;
                Stoptime();
            }

            @Override
            public void uiDeviceDisconnected(final BluetoothGatt gatt,
                                             final BluetoothDevice device) // If the device disconnects it will change local variables to indicate as such
            {
                Log.d("Disconnected", "device disconnected????");
                deviceConnected = false;
                inrange = false;

            }



            @Override
            public void uiAvailableServices(BluetoothGatt gatt,
                                            BluetoothDevice device,
                                            List<BluetoothGattService> services) // This function tells the phone what sort of messages the lighting controller can accept
            {
                for(BluetoothGattService service : services)
                {
                    String serviceName = BleNamesResolver.resolveUuid(service.getUuid().toString());
                    Log.d("DEBUG", serviceName);

                }
            }

            @Override
            public void uiNewRssiAvailable(final BluetoothGatt gatt, final BluetoothDevice device, final int rssi) // This function keeps track of the RSSI value sent by the lighting controller
            {
                setRSSI(rssi);

            }



            @Override
            public void uiNewValueForCharacteristic(BluetoothGatt gatt,
                                                    BluetoothDevice device, BluetoothGattService service,
                                                    BluetoothGattCharacteristic ch, String strValue, int intValue,
                                                    byte[] rawValue, String timestamp) // this function allows the phone to communicate messages to the lighting controller
            {
                super.uiNewValueForCharacteristic(gatt, device, service, ch, strValue, intValue, rawValue, timestamp);
                Log.d("IMPORTANT", "uiNewValueForCharacteristic");
                for (byte b:rawValue)
                {
                    Log.d("IMPORTANT", "Val: " + b);
                }
            }

            public void uiSuccessfulWrite(BluetoothGatt gatt, BluetoothDevice device,
                                          BluetoothGattService service, BluetoothGattCharacteristic ch,
                                          String description) // This function lets the phone know that the lighting controller recieved message correctly
            {
                BluetoothGattCharacteristic c;


            }
        });

        if(!mBleWrapper.checkBleHardwareAvailable()) // If the user's phone does not have Bluetooth Low Energy the application will close
        { finish();}


        /**
         * Below is all of the code '15 wrote.
         */


        // END IMPORT CODE
        // Initialize mode objects.
        modes = new modeObj[NUM_MODES];
        for(int i = 0; i < NUM_MODES; i++){ //Creates the ambient profiles
            modes[i] = new modeObj(i);
        }
        modes[0].objLampR.setMasterColor(-16776961);    // Setting default colors for all the modes
        modes[0].objLampL.setMasterColor(-16776961);    // The Default color of Relax is Blue
        modes[1].objLampR.setMasterColor( -65536);      // The Default color of Night Drive is Red
        modes[1].objLampL.setMasterColor( -65536);      //The Default color of Custom Modes is White
        for(int k = 2; k< NUM_MODES; k++){
            modes[k].objLampL.setMasterColor(-1);
            modes[k].objLampR.setMasterColor(-1);
        }

        /*
        Checks to see if user has previously set colors of ambient profiles
        and changes the color settings accordingly
         */
        for(int j= 0; j < NUM_MODES; j++)
        {
            if(ReadModeObject(j,"L") != 0)
                modes[j].objLampL.setMasterColor(ReadModeObject(j,"L"));
            if(ReadModeObject(j,"R") != 0)
                modes[j].objLampR.setMasterColor(ReadModeObject(j,"R"));
        }

        // Initialize toggle buttons
        modeToggleButtons = new boolean[NUM_TOGGLE_BUTTONS];
        for (int i = 0; i < NUM_TOGGLE_BUTTONS; i++)
            modeToggleButtons[i] = false;


        myListener = new CustomOnItemSelectedListener(); // Initializes the Drop-down ambient profile menu

        /*
        Set the active ambient lighting mode to the first in the list activeAmbient[]
         */
        activeAmbientMode = 0;

        // Initialize seek bar
        overhead_intensity = 120;
        reading_lamp_intensity = 120;

        modes_activity();
        Scantime();
	}

    /**
     * onResume: this function is called when the user reopens the app
     */
	@Override
	protected void onResume()
    {
		super.onResume();

        //check for ble enabled on each resume
        if(!mBleWrapper.isBtEnabled())
        {
            //not enabled, request user to turn it on
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            startActivity(enableBtIntent);
            finish();
        }

	}

	@Override
	protected void onStop() {
		super.onStop();

	}


	@Override
	protected void onDestroy() {
		super.onDestroy();

	}

    // Import Code

    /**
     * Scantime: This function starts the scan process
     */
    public void Scantime()
    {
        if(mBleWrapper != null)
        {
            mBleWrapper.startScanning();
        }
    }


    /**
     * Stoptime: This function stops the scan process
     */
    public void Stoptime()
    {
        if(mBleWrapper != null)
        {
            mBleWrapper.stopScanning();
        }
    }


    private int RSSI_value_count = 0; // global to maintain value between iteration of function call
    private int[] RSSI_avg_array = new int[10];

    /**
     * avgRssiValues: Finds the average of ten RSSI values (running average)
     * @param rssi
     * @return
     */
    public int avgRssiValues(int rssi){
        if(firstConnection) {
            mDialog.dismiss();
            int numToAvg = 5; // Number of values needed to take average rssi.

            // Check for 8 identical rssi values in a row. If true device has disconnected

            deviceConnected = false;
            for (int i = 0; i < 8; i++)
                if (rssi_check[i] != rssi && !deviceConnected) {
                    deviceConnected = true;
                    rssi_check[i] = rssi;
                }

            if (!deviceConnected)
                inrange = false;

            changeConnectedText(deviceConnected);

            // while the device is connected the app will find the average RSSI value, and send the Arduino an acknowledgement packet every 5 seconds
            if (deviceConnected) {
                if (RSSI_value_count == numToAvg) {
                    int avgRSSI = 0;
                    for (int i = 0; i < numToAvg; i++) {
                        avgRSSI += RSSI_avg_array[i];
                    }
                    avgRSSI = (int) avgRSSI / numToAvg;
                    RSSI_value_count = 0;
                    if (deviceConnected)
                        notifyArduino();
                    return avgRSSI;
                } else {
                    RSSI_avg_array[RSSI_value_count] = lastKnownRssi;
                    RSSI_value_count += 1;
                }
            }
        }
        return 0;
    }

    /**
     * changeConnectedText: This function changes the test at the top of mode screen to reflect the
     *                      connection status of the app
     * @param connected
     */
    public void changeConnectedText(boolean connected)
    {
        if (!connected) {
            rssi_value.setTextColor(Color.RED);
            connect_disconnect_textView.setTextColor(Color.RED);
            connect_disconnect_textView.setText("Disconnected");
            rssi_value.setText("---");
        } else {
            rssi_value.setTextColor(Color.GREEN);
            connect_disconnect_textView.setTextColor(Color.GREEN);
            connect_disconnect_textView.setText("Connected");
        }
    }

    /**
     * checkInRange: This function checks if the phone has come inside a predetermined range
     *               (declared at top) and turns on the ambient and overhead lamps if it is in Range.
     * @param rssi
     */
    public void checkInRange(int rssi)
    {
        if(!inrange) {
            if (rssi > RSSI_THRESHOLD) {
                inrange = true;
                modeToggleButtons[0] = true;
                modeToggleButtons[1] = true;
                sendLightData();
                if(!inColorSeclector){
                    ToggleButton t1 = (ToggleButton) findViewById(R.id.color_select_toggle_button);
                    ToggleButton t2 = (ToggleButton) findViewById(R.id.overhead_toggle_button);
                    t1.setChecked(true);
                    t2.setChecked(true);
                }
            }
        }
        /* This portion handles when the phone goes "out of range" in terms of the RSSI threshold
        We decided not to turn off the lights if the phones goes "out of range" to due the finicky nature of RSSI.
        In our implementation, the lights will only turn off once the user has disconnected to the system
        else {
            if (rssi < RSSI_THRESHOLD) {
                inrange = false;
                modeToggleButtons[0] = false;
                modeToggleButtons[1] = false;
                sendLightData();
                if (!inColorSeclector) {
                    ToggleButton t1 = (ToggleButton) findViewById(R.id.color_select_toggle_button);
                    ToggleButton t2 = (ToggleButton) findViewById(R.id.overhead_toggle_button);
                    t1.setChecked(false);
                    t2.setChecked(false);
                }
            }
        }
        */
    }

    /**
     * setRSSI: Continuously averages RSSI value
     * @param rssi
     */
    public void setRSSI(int rssi)
    {
        lastKnownRssi = rssi;

        runOnUiThread(new Runnable() {
           @Override
            public void run() {
               //Average 10 RSSI values
               // Add values to array to be averaged
               int avgRSSI = avgRssiValues(lastKnownRssi);
               if (avgRSSI != 0) {
                   final String dataVal = "" + avgRSSI;
                   rssi_value.setText(dataVal);
                   checkInRange(avgRSSI);
               }
           }
        });


    }
    // END IMPORT CODE

    /**
     * onBackPressed: Handles when the user presses the back buttons. If the user is in the color
     *                selector screen they will taken back to the Lighting control screen. If the
     *                user is in the lighting control screen a dialogue will pop up asking if the
     *                user wants to disconnect or stay connected. If they hit disconnect they will
     *                disconnect from the lighting controller and return to main screen.
     */
    public void onBackPressed()
    {
        if (inColorSeclector) {
            EditText text = (EditText) findViewById(R.id.current_mode_edit_text);
            saveName(activeAmbientMode, text);
            AmbientModes[activeAmbientMode] = text.getText().toString();
            setContentView(R.layout.modes);
            modes_activity();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Exit Application");
            builder.setMessage("Do you wish to disconnect from the module");
            builder.setPositiveButton("Disconnect", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    mBleWrapper.disconnect();
                    finish();
                }
            });
            builder.setNegativeButton("Stay Connected", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                   return;
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    /**
     * modes_activity: Code that controls what happens in the lighting control screen.
     */
    public void modes_activity()
    {
        //Checks if the user has previously saved names for their custom profiles
         for(int i = 2; i < NUM_MODES; i++){
           if(readName(i) !=null)
              AmbientModes[i] = readName(i);
        }


        inColorSeclector= false;
        ArrayAdapter<String> stringArrayAdapter = new CustomAdapter(this,
                R.layout.spinner_rows, AmbientModes);

        // Set up spinner
        Spinner spinner = (Spinner)findViewById(R.id.ambient_select_spinner);
        spinner.setAdapter(stringArrayAdapter);
        spinner.setSelection(activeAmbientMode);
        spinner.setOnItemSelectedListener(myListener);

        // Initialize  overhead and reading scroll bars
        overhead_seekbar = (SeekBar) findViewById(R.id.overhead_seekbar);
        overhead_seekbar.setProgress((int) (overhead_intensity/2.55) );
        reading_seekbar = (SeekBar) findViewById(R.id.reading_seekbar);
        reading_seekbar.setProgress((int) (reading_lamp_intensity/2.55));



        //Initialize on/off buttons
        ToggleButton t0 = (ToggleButton)findViewById(R.id.color_select_toggle_button);
        ToggleButton t1 = (ToggleButton)findViewById(R.id.overhead_toggle_button);
        ToggleButton t2 = (ToggleButton)findViewById(R.id.reading_toggle_button);
        ToggleButton t3 = (ToggleButton)findViewById(R.id.demo_toggle_button);

        t0.setChecked(modeToggleButtons[0]);
        t1.setChecked(modeToggleButtons[1]);
        t2.setChecked(modeToggleButtons[2]);
        t3.setChecked(modeToggleButtons[3]);

        rssi_value = (TextView) findViewById(R.id.rssi_value);
        connect_disconnect_textView = (TextView) findViewById(R.id.connect_disconnect_textView);

        changeConnectedText(deviceConnected);

        overhead_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() // Instantly sends the data as the user adjusts scroll bars
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean bool) {
                overhead_intensity = (int) (progressValue * 2.55);
                // Send live value to arduino
                if(modeToggleButtons[1])
                    sendLightData();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {           }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {            }
        });
        reading_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean bool) {
                reading_lamp_intensity = (int) (progressValue * 2.55);
                // Send live value to arduino
                if(modeToggleButtons[2])
                    sendLightData();

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //Toast.makeText(getApplicationContext(), "Started tracking seekbar", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

// This function allows the user to set the RSSI threshold. It's useful for development purposes, but should not be in the final user version
// There is corresponding button in the modes.xml final
/*    public void RSSI_click(View view){
        EditText editText = (EditText) findViewById(R.id.RSSI_edit_text);
        RSSI_THRESHOLD = Integer.parseInt( editText.getText().toString());
        Toast.makeText(getApplicationContext(), Integer.toString(RSSI_THRESHOLD), Toast.LENGTH_SHORT).show();


    }*/

    /**
     * color_select_activity: determines what happens in the color selector screen
     * @param id Value of the view's id
     */
    public void color_select_activity(int id)
    {
        setContentView(R.layout.color_picker_screen);
        inColorSeclector = true;
        colorPicker = (ColorPicker) findViewById(R.id.colorPicker);
        // Always initialize color wheel to left ambient lamp.
        colorPicker.setColor(modes[activeAmbientMode].objLampL.getMasterColor());
        //saveModeObject(activeAmbientMode);
        EditText text = (EditText) findViewById(R.id.current_mode_edit_text);
        text.setText(AmbientModes[activeAmbientMode]);
        colorPicker.addPropertyChangeListener("color", listener);
    }

    /**
     * zone_edit_activity: sets up the zone layout screen
     * @param id **no reason for this yet***********************************************************
     */
    public void zone_edit_activity(int id)
    {
        setContentView(R.layout.cabin_zones);
    }

    /**
     * edit_button_click: the bootstrap to get to the cabin zoning layout
     * @param view
     */
    public void edit_button_click(View view) {

        //gets the zone chart out
        zone_edit_activity(view.getId());
        //old stuff:
        //color_select_activity(view.getId());

    }

    /**
     * ambient_toggle_click: On/Off toggle for the ambient lighting
     * @param view
     */
    public void ambient_toggle_click(View view) {
        if(((ToggleButton) view).isChecked())
            modeToggleButtons[0] = true;
        else
            modeToggleButtons[0] = false;
        sendLightData();
    }

    /**
     * overhead_toggle_click: On/Off toggle for the overhead lighting
     * @param view
     */
    public void overhead_toggle_click(View view) {
        if(((ToggleButton) view).isChecked())
            modeToggleButtons[1] = true;
        else
            modeToggleButtons[1] = false;
        sendLightData();
    }

    /**
     * reading_toggle_click: On/Off toggle for the reading light
     * @param view
     */
    public void reading_toggle_click(View view) {
        if(((ToggleButton) view).isChecked())
            modeToggleButtons[2] = true;
        else
            modeToggleButtons[2] = false;
        sendLightData();
    }

    /**
     * demo_click: If the user hits the demo button, this code "greys" out the rest of the buttons
     *              and sends a special signal to Lighting controller
     * @param view
     */
    public void demo_click(View view)
    {
        ToggleButton ambient_toggle = (ToggleButton) findViewById(R.id.color_select_toggle_button);
        ToggleButton overhead_toggle = (ToggleButton) findViewById(R.id.overhead_toggle_button);
        ToggleButton reading_toggle = (ToggleButton) findViewById(R.id.reading_toggle_button);
        Button ambient_edit = (Button) findViewById(R.id.ambient_edit_button);
        SeekBar s1 = (SeekBar) findViewById(R.id.overhead_seekbar);
        SeekBar s2 = (SeekBar) findViewById(R.id.reading_seekbar);
        byte[] data = { 0x02, 0x00};
        if(((ToggleButton) view).isChecked()) {
            modeToggleButtons[3] = true;

            ambient_toggle.setEnabled(false);
            overhead_toggle.setEnabled(false);
            reading_toggle.setEnabled(false);
            s1.setEnabled(false);
            s2.setEnabled(false);
            ambient_edit.setEnabled(false);

            data[1] = (byte) 1;
            BluetoothGatt gatt;
            BluetoothGattCharacteristic c2;
            gatt = mBleWrapper.getGatt();
            try {
                c2 = gatt.getService(UUID_RBL).getCharacteristic(UUID_TX);
                mBleWrapper.writeDataToCharacteristic(c2, data);
            }

            catch( NullPointerException e ) {  }

        }else{
            modeToggleButtons[3] = false;
            ambient_toggle.setEnabled(true);
            overhead_toggle.setEnabled(true);
            reading_toggle.setEnabled(true);
            s1.setEnabled(true);
            s2.setEnabled(true);
            ambient_edit.setEnabled(true);

            BluetoothGatt gatt;
            BluetoothGattCharacteristic c2;
            gatt = mBleWrapper.getGatt();
            try {
                c2 = gatt.getService(UUID_RBL).getCharacteristic(UUID_TX);
                mBleWrapper.writeDataToCharacteristic(c2, data);
            }

            catch( NullPointerException e ) {  }

            sendLightData();
        }




    }


    // END ON/OFF BUTTONS

    /**
     * CustomOnItemSelectedListener
     * Handles clicks to the ambient profile drop-down menu
     */
    public class CustomOnItemSelectedListener implements AdapterView.OnItemSelectedListener
    {
        /**
         * onItemSelected:
         * @param parent
         * @param view
         * @param pos
         * @param id
         */
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            activeAmbientMode = (int) id;
            sendLightData();


        }

        /**
         * onNothingSelected:
         * @param arg0
         */
        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            // TODO Auto-generated method stub
        }
    }

    public void set_color_left(View view) {
        modeToggleButtons[0] = true;
        modes[activeAmbientMode].objLampL.setMasterColor(colorPicker.getColor());
        saveModeObject(activeAmbientMode); // Save color data into file
        sendLightData();

    }

    public void set_color_right(View view) {
        modeToggleButtons[0] = true;
        modes[activeAmbientMode].objLampR.setMasterColor(colorPicker.getColor());
        saveModeObject(activeAmbientMode); // Save color data into file
        sendLightData();
    }

    /**
     * set_zone_color:
     * @param view
     */
    public void set_zone_color(View view) {
        modeToggleButtons[0] = true;
        modes[activeAmbientMode].objLampR.setMasterColor(colorPicker.getColor());
        saveModeObject(activeAmbientMode); // Save color data into file
        sendLightData();
    }

    /**
     * sendLightData: This function forms the packets that are sent out to the lighting controller
     *                when light state is changed
     * (more info on the packets contained in the Project Report. Section 4.2.2)
     */
    public void sendLightData()
    {
        byte[] data = { 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        if(modeToggleButtons[0]) {
            data[1] = (byte) modes[activeAmbientMode].objLampL.getR();
            data[2] = (byte) modes[activeAmbientMode].objLampL.getG();
            data[3] = (byte) modes[activeAmbientMode].objLampL.getB();

            data[4] = (byte) modes[activeAmbientMode].objLampR.getR();
            data[5] = (byte) modes[activeAmbientMode].objLampR.getG();
            data[6] = (byte) modes[activeAmbientMode].objLampR.getB();
        }
        if(modeToggleButtons[1])
            data[7] = (byte) overhead_intensity;
        if(modeToggleButtons[2])
            data[8] = (byte) reading_lamp_intensity;


        BluetoothGatt gatt;
        BluetoothGattCharacteristic c2;
        gatt = mBleWrapper.getGatt();
        try {
            c2 = gatt.getService(UUID_RBL).getCharacteristic(UUID_TX);
            mBleWrapper.writeDataToCharacteristic(c2, data);
        }

        catch( NullPointerException e ) {  }

    }

    /**
     * notifyArduino: This function forms the acknowledgement packets sent by the phone to the
     *                lighting controller. See section 4.2.2 of project report for more info
     */
    public void notifyArduino()
    {
        try {
            byte[] data = {0x00};
            BluetoothGatt gatt;
            BluetoothGattCharacteristic c2;
            gatt = mBleWrapper.getGatt();
            if (gatt == null) {
                Log.d("NULLPOINT", "GATT is NULL");
            }
            c2 = gatt.getService(UUID_RBL).getCharacteristic(UUID_TX);

            mBleWrapper.writeDataToCharacteristic(c2, data);

        } catch (NullPointerException e) {
            Log.d("NULLPOINT", "Avoided exception w/ try catch block");
        }
    }


    /**
     * saveName: Saves the name of the custom profile in a text file stored locally on the phone
     * @param modeToRead
     * @param text
     */
    private void saveName(int modeToRead, EditText text) //
    {
        String filename = AmbientModes[modeToRead]+".txt";
        try {
            FileOutputStream fileout = openFileOutput(filename, MODE_PRIVATE);
            OutputStreamWriter outputwriter = new OutputStreamWriter(fileout);
            outputwriter.write(text.getText().toString());
            outputwriter.close();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * readName: Looks for a file with the name of the custom profile. If the file exist then it
     *           reads the file and returns the name. If can't find the file it throws an exception
     *           which is caught.
     * @param modeToRead
     * @return
     */
    private String readName(int modeToRead)
    {
        String filename = AmbientModes[modeToRead] + ".txt";
        String s = null;
        try {
            FileInputStream filein = openFileInput(filename);
            InputStreamReader InputRead = new InputStreamReader(filein);

            char[] inputBuffer = new char[100];
            s = "";
            int charRead;

            while ((charRead = InputRead.read(inputBuffer)) > 0) {
                String readstring = String.copyValueOf(inputBuffer, 0, charRead);
                s += readstring;
            }
            InputRead.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return s;
    }

    /**
     * saveModeObject: Saves the color of the both lamps in text files stored locally on the phone
     *                 converts the color, which is stored as an integer, and converts it to a
     *                 string so it can be saved as a text file.
     * @param currentModeObject
     */
    private void saveModeObject(int currentModeObject)
    {
        String filename;
        filename = "ModeObject" +  Integer.toString(currentModeObject) + "R.txt";

        try {
            FileOutputStream fileout = openFileOutput(filename, MODE_PRIVATE);
            OutputStreamWriter outputwriter = new OutputStreamWriter(fileout);
            outputwriter.write(Integer.toString(modes[currentModeObject].objLampR.getMasterColor()));
            outputwriter.close();

        }catch(Exception e) {
            e.printStackTrace();
        }
        filename = "ModeObject" +  Integer.toString(currentModeObject) + "L.txt";

        try {
            FileOutputStream fileout = openFileOutput(filename, MODE_PRIVATE);
            OutputStreamWriter outputwriter = new OutputStreamWriter(fileout);
            outputwriter.write(Integer.toString(modes[currentModeObject].objLampL.getMasterColor()));
            outputwriter.close();

        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ReadModeObject: Reads the text file that holds the color of each lamp. It converts the string
     *                 that it reads and converts it to an integer and returns that integer.
     * @param currentModeObject
     * @param left_right
     * @return
     */
    private int ReadModeObject(int currentModeObject, String left_right) //
    //
    {

        String fileName = "ModeObject" + Integer.toString(currentModeObject) + left_right +".txt";
        String s = null;
        int color = 0;
        try {
            FileInputStream filein = openFileInput(fileName);
            InputStreamReader InputRead = new InputStreamReader(filein);

            char[] inputBuffer = new char[100];
            s = "";
            int charRead;

            while ((charRead = InputRead.read(inputBuffer)) > 0) {
                String readstring = String.copyValueOf(inputBuffer, 0, charRead);
                s += readstring;
            }
            InputRead.close();
        } catch (Exception e) {
            e.printStackTrace();

        }
        if(s!= null)
            color = Integer.parseInt(s);

        return color;
    }

    /**
     * showRoundProcessDialog: This function controls the loading dialogue
     * @param mContext
     * @param layout
     */
    public void showRoundProcessDialog(Context mContext, int layout)
    {
        DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode,
                                 KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_HOME
                        || keyCode == KeyEvent.KEYCODE_SEARCH) {
                    return true;
                }
                if(keyCode == KeyEvent.KEYCODE_BACK){
                    dialog.dismiss();
                    finish();
                }
                return false;
            }
        };

        mDialog = new AlertDialog.Builder(mContext).create();
        mDialog.setOnKeyListener(keyListener);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.show();
        mDialog.setContentView(layout);
    }
}
