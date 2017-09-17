package com.intersvyaz.dlnaexample;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.android.FixedAndroidLogHandler;
import org.fourthline.cling.controlpoint.ActionCallback;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.ServiceId;
import org.fourthline.cling.model.types.UDAServiceId;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.avtransport.callback.Play;
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI;
import org.fourthline.cling.support.avtransport.callback.Stop;
import org.seamless.util.logging.LoggingUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity {
    public final String TAG = "dlna";
    private AndroidUpnpService upnpService;
    private BrowseRegistryListener registryListener = new BrowseRegistryListener();
    private String SERVICE_AV_TRANSPORT_ID = "AVTransport";
    private String SERVICE_CONNECTION_MANAGER = "ConnectionManager";
    private String SERVICE_RENDERING_CONTROL = "RenderingControl";
    private ArrayAdapter<DeviceDisplay> listAdapter;
    private Device selectedDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fix the logging integration between java.util.logging and Android internal logging
        LoggingUtil.resetRootHandler(
                new FixedAndroidLogHandler()
        );
        // Now you can enable logging as needed for various categories of Cling:
        Logger.getLogger("org.fourthline.cling").setLevel(Level.FINEST);

        // This will start the UPnP service if it wasn't already started
        getApplicationContext().bindService(
                new Intent(this, AndroidUpnpServiceImpl.class),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice);
        ListView lv = (ListView) findViewById(R.id.list_devices);
        lv.setAdapter(listAdapter);
        lv.setOnItemClickListener(onDeviceClickListener());
        lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(onPlayListener());
    }

    /**
     * Соединение с сервисом upnpService
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            upnpService = (AndroidUpnpService) service;
            // Clear the list
            listAdapter.clear();
            // Get ready for future device advertisements
            upnpService.getRegistry().addListener(registryListener);
            // Now add all devices to the list we already know about
            for (Device device : upnpService.getRegistry().getDevices()) {
                registryListener.deviceAdded(device);
            }
            // Search asynchronously for all devices, they will respond soon
            upnpService.getControlPoint().search();
        }

        public void onServiceDisconnected(ComponentName className) {
            upnpService = null;
        }
    };

    @NonNull
    private AdapterView.OnItemClickListener onDeviceClickListener() {
        return new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                DeviceDisplay entry = (DeviceDisplay) parent.getItemAtPosition(position);
                if (!isRenderingControl(entry.getDevice())) {
                    Toast.makeText(getApplicationContext(), "Device is not renderer", Toast.LENGTH_SHORT).show();
                    selectedDevice = null;
                } else {
                    selectedDevice = entry.getDevice();
                }
            }
        };
    }

    private View.OnClickListener onPlayListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText edit = (EditText) findViewById(R.id.edit_url);
                String url = edit.getText().toString();
                if (selectedDevice == null || TextUtils.isEmpty(url)) {
                    Toast.makeText(getApplicationContext(), "No selected device or empty url", Toast.LENGTH_SHORT).show();
                } else {
                    play(selectedDevice, edit.getText().toString());
                }
            }
        };
    }

    private void play(Device device, String url) {
        //@todo: сначала нужно узнать, умеет ли устройство проигрывать медиаданные
        // GetInfo(device);
        executeAVTransportURI(device, url);
    }

    public void executeAVTransportURI(final Device device, String url) {
        ServiceId AVTransportId = new UDAServiceId(SERVICE_AV_TRANSPORT_ID);
        Service service = device.findService(AVTransportId);
        MediaInfo m = MediaInfo.createFromUrl(url, "video/mpeg", "Sample video");
        ActionCallback callback = new SetAVTransportURI(service, url, m.getMetaData()) {
            @Override
            public void success(ActionInvocation invocation) {
                super.success(invocation);
                executeStop(device);
            }

            @Override
            public void failure(ActionInvocation arg0, UpnpResponse arg1,
                                String arg2) {
                String message = "failed to SetAVTransportURI:" + arg2;
                Log.e(TAG, message);
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        };
        upnpService.getControlPoint().execute(callback);
    }

    public void executeStop(final Device device) {
        ServiceId AVTransportId = new UDAServiceId(SERVICE_AV_TRANSPORT_ID);
        Service service = device.findService(AVTransportId);
        ActionCallback playcallback = new Stop(service) {
            @Override
            public void success(ActionInvocation invocation) {
                executePlay(device);
            }

            @Override
            public void failure(ActionInvocation arg0, UpnpResponse arg1,
                                String arg2) {
                String message = "Failed to stop:" + arg2;
                Log.e(TAG, message);
                executePlay(device);
            }
        };
        upnpService.getControlPoint().execute(playcallback);
    }

    public void executePlay(Device device) {
        ServiceId AVTransportId = new UDAServiceId(SERVICE_AV_TRANSPORT_ID);
        Service service = device.findService(AVTransportId);
        ActionCallback playcallback = new Play(service) {
            @Override
            public void success(ActionInvocation invocation) {
                Toast.makeText(getApplicationContext(), "Success playcallback", Toast.LENGTH_LONG).show();
            }

            @Override
            public void failure(ActionInvocation arg0, UpnpResponse arg1,
                                String arg2) {
                String message = "Failed to play:" + arg2;
                Log.e(TAG, message);
                Log.d(TAG, arg1.toString());
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        };
        upnpService.getControlPoint().execute(playcallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (upnpService != null) {
            upnpService.getRegistry().removeListener(registryListener);
        }
        // This will stop the UPnP service if nobody else is bound to it
        getApplicationContext().unbindService(serviceConnection);
    }

    protected boolean isRenderingControl(Device device) {
        return device.findService(new UDAServiceType(SERVICE_RENDERING_CONTROL)) != null;
    }

    /**
     * Слушатель на изменение реестра upnp
     */
    private class BrowseRegistryListener extends DefaultRegistryListener {
        /* Discovery performance optimization for very slow Android devices! */
        @Override
        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceDiscoveryFailed(Registry registry, final RemoteDevice device, final Exception ex) {
            deviceRemoved(device);
        }

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            deviceAdded(device);
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            deviceRemoved(device);
        }

        @Override
        public void localDeviceAdded(Registry registry, LocalDevice device) {
            deviceAdded(device);
        }

        @Override
        public void localDeviceRemoved(Registry registry, LocalDevice device) {
            deviceRemoved(device);
        }

        public void deviceAdded(final Device device) {
            runOnUiThread(new Runnable() {
                public void run() {
                    DeviceDisplay d = new DeviceDisplay(device);
                    int position = listAdapter.getPosition(d);
                    if (position >= 0) {
                        // Device already in the list, re-set new value at same position
                        listAdapter.remove(d);
                        listAdapter.insert(d, position);
                    } else {
                        listAdapter.add(d);
                    }
                }
            });
        }

        public void deviceRemoved(final Device device) {
            runOnUiThread(new Runnable() {
                public void run() {
                    listAdapter.remove(new DeviceDisplay(device));
                }
            });
        }
    }
}
