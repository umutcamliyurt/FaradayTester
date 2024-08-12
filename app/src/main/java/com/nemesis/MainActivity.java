package com.nemesis.faradaytester;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellInfo;
import android.telephony.CellSignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private TextView networkStatusTextView;
    private ProgressBar progressBar;
    private Handler handler;
    private Runnable checkNetworkStatusRunnable;
    private static final int CHECK_INTERVAL_MS = 30000; // Check every 10 seconds
    private static final int PERMISSION_REQUEST_CODE = 1;
    private MediaPlayer mediaPlayer;

    // BroadcastReceiver to handle Bluetooth connection changes
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction()) ||
                    BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                checkStatuses(); // Re-check statuses when Bluetooth connection changes
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Ensure this is the correct layout file

        networkStatusTextView = findViewById(R.id.networkStatusTextView);
        progressBar = findViewById(R.id.progressBar);

        // Initialize MediaPlayer
        mediaPlayer = MediaPlayer.create(this, R.raw.beep);

        // Check and request permissions
        checkAndRequestPermissions();

        // Register Bluetooth receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);

        // Initialize the Handler with the main Looper
        handler = new Handler(Looper.getMainLooper());
        checkNetworkStatusRunnable = new Runnable() {
            @Override
            public void run() {
                showProgressBar(true);
                checkStatuses();
                playBeepSound();
                handler.postDelayed(() -> showProgressBar(false), CHECK_INTERVAL_MS);
                handler.postDelayed(this, CHECK_INTERVAL_MS); // Re-run after CHECK_INTERVAL_MS
            }
        };
    }

    private void showProgressBar(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? ProgressBar.VISIBLE : ProgressBar.GONE);
            Log.d("MainActivity", "ProgressBar visibility set to: " + (show ? "VISIBLE" : "GONE"));
        } else {
            Log.e("MainActivity", "ProgressBar is null");
        }
    }

    private void playBeepSound() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            // Restart the mediaPlayer if needed, or you may need to reset and prepare it if it's not looping.
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start checking network status when the activity becomes visible
        handler.post(checkNetworkStatusRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop checking network status when the activity is no longer visible
        handler.removeCallbacks(checkNetworkStatusRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister Bluetooth receiver
        unregisterReceiver(bluetoothReceiver);
        // Release MediaPlayer resources
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void checkStatuses() {
        StringBuilder statusBuilder = new StringBuilder();

        if (isNetworkConnected()) {
            statusBuilder.append("Connected to Network\n");
        } else {
            statusBuilder.append("No Network Connection\n");
        }

        if (isBluetoothConnected()) {
            statusBuilder.append("Bluetooth is Connected\n");
        } else {
            statusBuilder.append("Bluetooth is Not Connected\n");
        }

        // Check signal strength and cell tower info
        String cellTowerInfo = getCellTowerInfo();
        if (cellTowerInfo != null) {
            statusBuilder.append(cellTowerInfo);
        } else {
            statusBuilder.append("Unable to retrieve cell tower info\n");
        }

        networkStatusTextView.setText(statusBuilder.toString());
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(activeNetwork);
                    return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                }
            } else {
                NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        }
        return false;
    }

    private boolean isBluetoothConnected() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            return false; // Bluetooth is not supported on this device
        }

        List<BluetoothDevice> connectedDevices;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        } else {
            connectedDevices = new ArrayList<>(bluetoothAdapter.getBondedDevices());
        }
        return !connectedDevices.isEmpty();
    }

    private void checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION // Required for cell tower info
        };

        // Check if permissions are already granted
        boolean permissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }

        if (!permissionsGranted) {
            // Request permissions if not granted
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private String getCellTowerInfo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Permission not granted to access cell tower information.";
        }

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
            if (cellInfoList != null && !cellInfoList.isEmpty()) {
                StringBuilder cellInfoBuilder = new StringBuilder();
                for (CellInfo cellInfo : cellInfoList) {
                    if (cellInfo instanceof android.telephony.CellInfoGsm) {
                        CellSignalStrength cellSignalStrength = ((android.telephony.CellInfoGsm) cellInfo).getCellSignalStrength();
                        cellInfoBuilder.append("GSM Signal Strength: ")
                                .append(cellSignalStrength.getDbm())
                                .append(" dBm\n");
                    } else if (cellInfo instanceof android.telephony.CellInfoLte) {
                        CellSignalStrength cellSignalStrength = ((android.telephony.CellInfoLte) cellInfo).getCellSignalStrength();
                        cellInfoBuilder.append("LTE Signal Strength: ")
                                .append(cellSignalStrength.getDbm())
                                .append(" dBm\n");
                    } else if (cellInfo instanceof android.telephony.CellInfoWcdma) {
                        CellSignalStrength cellSignalStrength = ((android.telephony.CellInfoWcdma) cellInfo).getCellSignalStrength();
                        cellInfoBuilder.append("WCDMA Signal Strength: ")
                                .append(cellSignalStrength.getDbm())
                                .append(" dBm\n");
                    }
                    // Handle other types of CellInfo if needed
                }
                return cellInfoBuilder.toString();
            }
        }
        return "No cell tower information available"; // Return a message if cell info could not be retrieved
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                // Permissions granted, update statuses
                checkStatuses();
            } else {
                // Handle the case where permissions are not granted
                networkStatusTextView.setText("Permissions not granted. Some functionality may be limited.");
            }
        }
    }
}
