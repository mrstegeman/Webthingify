package be.humanoids.webthingify;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.util.Log;

import org.mozilla.iot.webthing.Property;
import org.mozilla.iot.webthing.Thing;
import org.mozilla.iot.webthing.Value;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class Phone extends Thing implements SensorEventListener {
    private final SensorManager sensorManager;
    private final CameraManager cameraManager;
    private final Vibrator vibrator;
    private Value<Double> brightness;
    private Value<Double> proximity;
    private Value<Double> pressure;
    private Value<Double> humidity;
    private Value<Integer> battery;
    private Value<Boolean> charging;
    private CameraManager.TorchCallback torchCallback = null;

    private String cameraId = null;

    Phone(String name, SensorManager sensors, BatteryManager batteries, CameraManager cameras, Vibrator vib) {
        super(name,
                Arrays.asList("OnOffSwitch", "Light"),
                "An Android phone"
        );

        sensorManager = sensors;
        cameraManager = cameras;
        vibrator = vib;

        try {
            String[] cams = cameraManager.getCameraIdList();
            for (String cam : cams) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cam);
                try {
                    if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                        cameraId = cam;
                        break;
                    }
                }
                catch(NullPointerException e) {
                    Log.d("wt:cam", "No characteristics for camera ".concat(cam));
                }
            }
            if (cameraId != null) {
                Map<String, Object> onDescription = new HashMap<>();
                onDescription.put("@type", "OnOffProperty");
                onDescription.put("label", "Flashlight On/Off");
                onDescription.put("type", "boolean");
                onDescription.put("description", "Whether the flashlight is turned on");
                final Value<Boolean> on = new Value<>(true, newValue -> {
                    try {
                        cameraManager.setTorchMode(cameraId, newValue);
                    } catch (CameraAccessException e) {
                        // e.printStackTrace();
                    }
                });
                torchCallback = new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(@NonNull String camId, boolean enabled) {
                        super.onTorchModeChanged(camId, enabled);
                        if (cameraId.equals(camId)) {
                            on.set(enabled);
                        }
                    }
                };
                cameraManager.registerTorchCallback(torchCallback, null);

                addProperty(new Property<>(this, "on", on, onDescription));
            }
        } catch (CameraAccessException e) {
            Log.w("wt:flash", "Error when making flash property", e);
        }

        Map<String, Object> batteryDescription = new HashMap<>();
        batteryDescription.put("@type", "LevelProperty");
        batteryDescription.put("label", "Battery");
        batteryDescription.put("unit", "percent");
        batteryDescription.put("type", "number");
        batteryDescription.put("description", "Battery charge of the device");
        batteryDescription.put("readOnly", true);

        battery = new Value<>(batteries.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));

        addProperty(new Property<>(this, "battery", battery, batteryDescription));

        Map<String, Object> chargingDescription = new HashMap<>();
        chargingDescription.put("type", "boolean");
        chargingDescription.put("readOnly", true);
        chargingDescription.put("label", "Charging");
        chargingDescription.put("description", "Device is plugged in and charging");

        charging = new Value<>(batteries.isCharging());

        addProperty(new Property<>(this, "charging", charging, chargingDescription));

        Sensor brightnessSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (brightnessSensor != null) {
            Map<String, Object> brightnessDescription = new HashMap<>();
            brightnessDescription.put("@type", "LevelProperty");
            brightnessDescription.put("type", "number");
            brightnessDescription.put("readOnly", true);
            brightnessDescription.put("label", "Brightness");
            brightnessDescription.put("unit", "lux");

            brightness = new Value<>(0.0);

            addProperty(new Property<>(this, "brightness", brightness, brightnessDescription));
            sensorManager.registerListener(this, brightnessSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Map<String, Object> loudnessDescription = new HashMap<>();
        loudnessDescription.put("type", "number");
        loudnessDescription.put("unit", "decibel");
        loudnessDescription.put("readOnly", true);
        loudnessDescription.put("label", "Loudness");

        Value<Double> loudness = new Value<>(0.0);

        addProperty(new Property<>(this, "loudness", loudness, loudnessDescription));

        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor != null) {
            Map<String, Object> proximityDescription = new HashMap<>();
            proximityDescription.put("type", "number");
            proximityDescription.put("readOnly", true);
            proximityDescription.put("label", "Proximity");
            proximityDescription.put("unit", "centimeter");

            proximity = new Value<>(0.0);

            addProperty(new Property<>(this, "proximity", proximity, proximityDescription));
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (pressureSensor != null) {
            Map<String, Object> pressureDescription = new HashMap<>();
            pressureDescription.put("type", "number");
            pressureDescription.put("readOnly", true);
            pressureDescription.put("unit", "hectopascal");
            pressureDescription.put("label", "Pressure");

            pressure = new Value<>(0.0);

            addProperty(new Property<>(this, "pressure", pressure, pressureDescription));
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        if (humiditySensor != null) {
            Map<String, Object> humidityDescription = new HashMap<>();
            humidityDescription.put("@type", "LevelProperty");
            humidityDescription.put("type", "number");
            humidityDescription.put("readOnly", true);
            humidityDescription.put("unit", "percent");
            humidityDescription.put("label", "Pressure");

            humidity = new Value<>(0.0);

            addProperty(new Property<>(this, "humidity", humidity, humidityDescription));
            sensorManager.registerListener(this, humiditySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        //TODO ambient temp?
        //TODO add property for camera
        //TODO action to take snapshot

        Map<String, Object> vibrateDescription = new HashMap<>();
        vibrateDescription.put("label", "Vibrate");
        addAvailableAction("vibrate", vibrateDescription, VibrateAction.class);
    }

    void vibrate() {
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    void setCharging(boolean isCharging) {
        charging.set(isCharging);
    }

    void setBattery(int batteryPercentage) {
        battery.set(batteryPercentage);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LIGHT:
                brightness.set((double) event.values[0]);
                break;
            case Sensor.TYPE_PRESSURE:
                pressure.set((double) event.values[0]);
                break;
            case Sensor.TYPE_PROXIMITY:
                proximity.set((double) event.values[0]);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                humidity.set((double) event.values[0]);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    void onDestroy() {
        sensorManager.unregisterListener(this);
        if (torchCallback != null) {
            cameraManager.unregisterTorchCallback(torchCallback);
        }

    }
}
