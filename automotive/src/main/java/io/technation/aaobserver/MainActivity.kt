package io.technation.aaobserver

import android.app.Activity
import android.car.*
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView


class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"

        private fun READEABLE_MW(mw: Float): String {
            if (mw == 0f) {
                return "000 mW"
            }
            val powermW = ( 1000000000/mw )*200
            if (powermW / 1000 < 1) {
                return "$powermW mW"
            }
            var power = powermW / 1000
            if (power / 1000 < 1) {
                return "$power W"
            }
            power /= 1000
            return "$power kW"
        }
    }

    private var isPermissionAlreadyAsked = false
    private lateinit var instantChargeRateTextView: TextView
    private lateinit var rawInstantChargeRateTextView: TextView
    //private lateinit var propertiesTableTableLayout: TableLayout
    private lateinit var propertiesValueTextViews: MutableMap<Int, TextView>

    /** Car API. */
    private lateinit var car : Car
    private val permissions = arrayOf(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS,
        Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME,
        Car.PERMISSION_PRIVILEGED_CAR_INFO,
        Car.PERMISSION_CAR_NAVIGATION_MANAGER,
        Car.PERMISSION_CONTROL_CAR_ENERGY,
        Car.PERMISSION_CONTROL_DISPLAY_UNITS,
        Car.PERMISSION_CONTROL_INTERIOR_LIGHTS,
        Car.PERMISSION_ENERGY,
        Car.PERMISSION_ENERGY_PORTS,
        Car.PERMISSION_EXTERIOR_ENVIRONMENT,
        Car.PERMISSION_IDENTIFICATION,
        Car.PERMISSION_POWERTRAIN,
        Car.PERMISSION_PRIVILEGED_CAR_INFO,
        Car.PERMISSION_READ_CAR_POWER_POLICY,
        Car.PERMISSION_READ_DISPLAY_UNITS,
        Car.PERMISSION_READ_INTERIOR_LIGHTS,
        Car.PERMISSION_READ_STEERING_STATE,
        Car.PERMISSION_SPEED,
    )

    /**
     * An API to read VHAL (vehicle hardware access layer) properties. List of vehicle properties
     * can be found in {@link VehiclePropertyIds}.
     *
     * <p>https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager
     */

    private lateinit var carPropertyManager: CarPropertyManager

    private var carPropertyListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<Any>) {
            Log.d(TAG, "Received on changed car property event id " + value.propertyId + " value : " + value.value)
            if (value.propertyId == VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE) {
                instantChargeRateTextView.text = READEABLE_MW(value.value as Float)
                rawInstantChargeRateTextView.text = value.value.toString()
            }
            propertiesValueTextViews[value.propertyId]?.text = propertyBeautifier(value)
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "Received error car property event, propId=$propId")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "OnCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        instantChargeRateTextView = findViewById(R.id.instantChargeRateTextView)
        rawInstantChargeRateTextView = findViewById(R.id.rawInstantChargeRateTextView)


        // createCar() returns a "Car" object to access car service APIs. It can return null if
        // car service is not yet ready but that is not a common case and can happen on rare cases
        // (for example car service crashes) so the receiver should be ready for a null car object.
        //
        // Other variants of this API allows more control over car service functionality (such as
        // handling car service crashes graciously). Please see the SDK documentation for this.
        car = Car.createCar(this)
        Log.d("MainActivity", "OnCreate3")
        carPropertyManager = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val carPropertyList = carPropertyManager.propertyList
        propertiesValueTextViews= mutableMapOf()
        initPropertiesTable(carPropertyList)

        // Subscribes to the gear change events.
        registerCallback(carPropertyManager, carPropertyList, carPropertyListener)

    }

    override fun onDestroy() {
        Log.d("MainActivity", "onDestroy")
        super.onDestroy()
        car.disconnect()
    }

    override fun onResume() {
        Log.d("MainActivity", "onResume")
        super.onResume()
        var isPermissionMissing = false
        for (perm in permissions) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                isPermissionMissing = true
                Log.d("MainActivity", "Missing perm : $perm")
            }
        }
        if(isPermissionMissing && !isPermissionAlreadyAsked) {
            isPermissionAlreadyAsked = true
            Log.d("MainActivity", "Request perm")
            requestPermissions(permissions, 0)
        }
    }

    private fun initPropertiesTable(carPropertyList: List<CarPropertyConfig<*>>) {

        val stk: TableLayout = findViewById(R.id.propertiesTable)
        for (carProperty in carPropertyList) {
            Log.d(TAG, "carProperty - " + VehiclePropertyIds.toString(carProperty.propertyId))

            val tbrow = TableRow(this)
            val t1v = TextView(this)
            t1v.text = carProperty.propertyId.toString()
            t1v.setTextColor(Color.WHITE)
            t1v.gravity = Gravity.CENTER
            tbrow.addView(t1v)
            val t2v = TextView(this)
            t2v.text = VehiclePropertyIds.toString(carProperty.propertyId)
            t2v.setTextColor(Color.WHITE)
            t2v.gravity = Gravity.CENTER
            t2v.setPadding(10,5,10,5)
            tbrow.addView(t2v)
            val t3v = TextView(this)
            t3v.text = getString(R.string.main_property_value_placeholder)
            t3v.setTextColor(Color.WHITE)
            t3v.gravity = Gravity.CENTER
            tbrow.addView(t3v)
            propertiesValueTextViews[carProperty.propertyId] = t3v
            stk.addView(tbrow)

        }
    }

    private fun registerCallback(carPropertyManager: CarPropertyManager,
                                 carPropertyList: List<CarPropertyConfig<*>>,
                                 callback: CarPropertyManager.CarPropertyEventCallback){
        for (carProperty in carPropertyList) {
            carPropertyManager.registerCallback(
                callback,
                carProperty.propertyId,
                carProperty.maxSampleRate
            )
        }
    }

    private fun propertyBeautifier(prop: CarPropertyValue<Any>): String {
        when (prop.propertyId) {
            VehiclePropertyIds.GEAR_SELECTION -> return resolveGearProperty(prop.value as Int)
            VehiclePropertyIds.CURRENT_GEAR -> return resolveGearProperty(prop.value as Int)
            VehiclePropertyIds.IGNITION_STATE -> return  resolveIgnitionStateProperty(prop.value as Int)
            VehiclePropertyIds.WHEEL_TICK -> return  resolveWheelTickProperty(prop.value as Array<Long>)
        }
        return prop.value.toString()
    }

    private fun resolveGearProperty(value: Int): String {
        when (value) {
            VehicleGear.GEAR_UNKNOWN -> return "GEAR_UNKNOWN"
            VehicleGear.GEAR_PARK -> return "GEAR_PARK"
            VehicleGear.GEAR_NEUTRAL -> return "GEAR_NEUTRAL"
            VehicleGear.GEAR_REVERSE -> return "GEAR_REVERSE"
            VehicleGear.GEAR_DRIVE -> return "GEAR_DRIVE"
            VehicleGear.GEAR_FIRST -> return "GEAR_FIRST"
            VehicleGear.GEAR_SECOND -> return "GEAR_SECOND"
            VehicleGear.GEAR_THIRD -> return "GEAR_THIRD"
            VehicleGear.GEAR_FOURTH -> return "GEAR_FOURTH"
            VehicleGear.GEAR_FIFTH -> return "GEAR_FIFTH"
            VehicleGear.GEAR_SIXTH -> return "GEAR_SIXTH"
            VehicleGear.GEAR_SEVENTH -> return "GEAR_SEVENTH"
            VehicleGear.GEAR_EIGHTH -> return "GEAR_EIGHTH"
            VehicleGear.GEAR_NINTH -> return "GEAR_NINTH"
        }
        return value.toString()
    }

    private fun resolveIgnitionStateProperty(value: Int): String {
        when (value) {
            VehicleIgnitionState.ACC -> return "ACC"
            VehicleIgnitionState.LOCK -> return "LOCK"
            VehicleIgnitionState.OFF -> return "OFF"
            VehicleIgnitionState.ON -> return "ON"
            VehicleIgnitionState.START -> return "START"
            VehicleIgnitionState.UNDEFINED -> return "UNDEFINED"
        }
        return value.toString()
    }

    private fun resolveWheelTickProperty(value: Array<Long> ): String {
        return String.format("RST : %s\nFL : %s\nFR : %s\nRR : %s\nRL : %s\n", value[0], value[1], value[2], value[3], value[4])
    }
}