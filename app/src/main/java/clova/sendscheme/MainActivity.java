package clova.sendscheme;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        } else {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADMIN, android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            // Bluetoothがサポートされていない場合の処理
            finish();
            Toast.makeText(this, "Bluetoothがサポートされていません。", Toast.LENGTH_SHORT).show();
        }

        if (!bluetoothAdapter.isEnabled()) {
            // Bluetoothが無効化されている場合は有効化する
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, 20);
        }

        findViewById(R.id.search_clova).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.search_clova).setEnabled(false);

                ClovaBTBroadcastReceiver clovaBTBroadcastReceiver = new ClovaBTBroadcastReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
                registerReceiver(clovaBTBroadcastReceiver, intentFilter);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(MainActivity.this, "権限がありません。", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                if (bluetoothAdapter.startDiscovery()) {
                    Toast.makeText(MainActivity.this, "Clovaを検索中", Toast.LENGTH_SHORT).show();
                } else {
                    findViewById(R.id.search_clova).setEnabled(true);
                    Toast.makeText(MainActivity.this, "検索に失敗しました。", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.disconnect_clova).setEnabled(false);

        findViewById(R.id.disconnect_clova).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothSocket.isConnected()) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    findViewById(R.id.disconnect_clova).setEnabled(false);
                    findViewById(R.id.search_clova).setEnabled(true);
                }
            }
        });

        findViewById(R.id.sendscheme_clova).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothSocket != null && !bluetoothSocket.isConnected()) {
                    Toast.makeText(MainActivity.this, "接続されていません。", Toast.LENGTH_SHORT).show();
                    return;
                }
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
                alertDialog.setTitle("Send Scheme");
                EditText editText = new EditText(MainActivity.this);
                alertDialog.setView(editText);
                alertDialog.setPositiveButton("Send", (id, dialog) -> {
                    String inputScheme = editText.getText().toString();
                    byte[] bytes = org.apache.commons.codec.binary.Base64.encodeBase64(inputScheme.getBytes());
                    byte[] bArr = new byte[bytes.length + 8];
                    bArr[0] = 65;
                    bArr[1] = 80;
                    bArr[2] = 83;
                    bArr[3] = 67;
                    bArr[4] = 82;
                    bArr[5] = 81;
                    if (bytes != null) {
                        int length = bytes.length;
                        System.arraycopy(new byte[]{(byte) (length >> 8), (byte) length}, 0, bArr, 6, 2);
                        System.arraycopy(bytes, 0, bArr, 8, length);
                    }
                    try {
                        bluetoothSocket.getOutputStream().write(bArr);
                        bluetoothSocket.getOutputStream().flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                alertDialog.show();
            }
        });

        findViewById(R.id.check_clova).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothSocket != null && !bluetoothSocket.isConnected()) {
                    Toast.makeText(MainActivity.this, "接続されていません。", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    bluetoothSocket.getOutputStream().write("APCD".getBytes(StandardCharsets.UTF_8));
                    bluetoothSocket.getOutputStream().flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    class ConnectThread extends Thread {
        private Context context;
        private BluetoothDevice bluetoothDevice;

        public ConnectThread (Context context, BluetoothDevice bluetoothDevice) {
            this.context = context;
            this.bluetoothDevice = bluetoothDevice;
        }

        @Override
        public void run() {
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                bluetoothSocket.connect();
            } catch (IOException e) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "接続に失敗しました: " + e, Toast.LENGTH_SHORT).show();
                        findViewById(R.id.search_clova).setEnabled(true);
                    }
                });
                e.printStackTrace();
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (bluetoothSocket.isConnected()) {
                        findViewById(R.id.disconnect_clova).setEnabled(true);
                        Toast.makeText(context, bluetoothDevice.getName() + "に接続しました。", Toast.LENGTH_SHORT).show();
                    }
                }
            }, 500);
        }
    }

    class ClovaBTBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE);
                Log.d("aihed", device.getName() + ": " + String.valueOf(device.getType()));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
                if (device.getName() != null && device.getName().startsWith("CLOVA-") && device.getType() != 2) {
                    // && device.getType() == 1
                    // Detect Clova
                    Log.d("ClovaSendScheme", "Detected CLOVA: " + device.getName());
                    unregisterReceiver(this);
                    ConnectThread connectThread = new ConnectThread(MainActivity.this, device);
                    connectThread.start();
                }
            }
        }
    }

}