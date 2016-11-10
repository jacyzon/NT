package tw.edu.ncku.csie.lens.nt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private boolean started;
    private int port = 20150;
    private byte[] b;
    private Timer timer;
    private DatagramSocket socket;
    private InetAddress ip;
    private String routerIP;
    private int interval = 100;
    private int packetLength = 100;
    private boolean colorFlip;
    private BroadcastReceiver wifiBroadReceiver;
    private ConnectivityManager connectivityManager;
    WifiManager.WifiLock lock;

    // view
    private View sendIndicator;
    private TextView tvIP;
    private EditText etInterval;
    private EditText etPacketLength;
    private Button btnStart;
    private Button btnSetInterval;
    private Button btnSetPacketLength;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // find view
        sendIndicator = findViewById(R.id.v_color);
        tvIP = (TextView) findViewById(R.id.tv_ip);
        etPacketLength = (EditText) findViewById(R.id.et_packet_len);
        etPacketLength.setText(String.valueOf(packetLength));

        btnSetPacketLength = (Button) findViewById(R.id.btn_set_packet_length);
        btnSetPacketLength.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                packetLength = Integer.valueOf(etPacketLength.getText().toString());
                initBytes();
            }
        });

        etInterval = (EditText) findViewById(R.id.et_interval);
        etInterval.setText(String.valueOf(interval));

        btnSetInterval = (Button) findViewById(R.id.bt_set_interval);
        btnSetInterval.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                interval = Integer.valueOf(etInterval.getText().toString());
            }
        });

        btnStart = (Button) findViewById(R.id.btn_start);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                started = !started;
                startOrStopSending();
            }
        });

        // get router IP
        routerIP = getRouterIP();
        tvIP.setText(routerIP);
        socket = initSocket(routerIP, port);

        // init random bytes
        initBytes();

        // register broadcast receiver
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiBroadReceiver = new WifiBroadcastReveiver();
        registerReceiver(wifiBroadReceiver, intentFilter);

        // start sending
        timer = new Timer();
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        lock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "NT");
        lock.acquire();
        started = true;
        startOrStopSending();
    }

    private void initBytes() {
        b = new byte[packetLength];
        new Random().nextBytes(b);
    }

    private void startOrStopSending() {
        if (started) {
            timer.schedule(new UDPTask(), 0);
            btnStart.setText("Stop");
        } else {
            timer = new Timer();
            btnStart.setText("Start");
        }
    }

    void closeCurrentSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    class WifiBroadcastReveiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String newRouterIP = getRouterIP();
            if (!newRouterIP.equals(routerIP)) {
                closeCurrentSocket();
                socket = initSocket(newRouterIP, port);
                routerIP = newRouterIP;
                tvIP.setText(routerIP);
            }
        }
    }

    class UDPTask extends TimerTask {

        @Override
        public void run() {
            if (!started) return;
            timer.schedule(new UDPTask(), interval);
            try {
                DatagramPacket packet = new DatagramPacket(b, b.length, ip, port);
                socket.send(packet);
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (colorFlip) {
                            sendIndicator.setBackgroundColor(
                                    getResources().getColor(R.color.holo_red_dark));
                        } else {
                            sendIndicator.setBackgroundColor(
                                    getResources().getColor(R.color.holo_red_light));
                        }
                        colorFlip = !colorFlip;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lock.release();
        started = false;
        timer.cancel();
        unregisterReceiver(wifiBroadReceiver);
    }

    private String getRouterIP() {
        final WifiManager manager = (WifiManager) super.getSystemService(WIFI_SERVICE);
        final DhcpInfo dhcp = manager.getDhcpInfo();
        byte[] bytes = BigInteger.valueOf(dhcp.gateway).toByteArray();
        ArrayUtils.reverse(bytes);
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "0.0.0.0";
        }
    }

    private DatagramSocket initSocket(String address, int port) {
        DatagramSocket socket = null;
        try {
            ip = InetAddress.getByName(address);
            socket = new DatagramSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return socket;
    }
}
