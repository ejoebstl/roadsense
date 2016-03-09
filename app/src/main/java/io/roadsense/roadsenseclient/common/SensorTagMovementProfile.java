/**************************************************************************************************
 Filename:       SensorTagMovementProfile.java

 Copyright (c) 2013 - 2015 Texas Instruments Incorporated

 All rights reserved not granted herein.
 Limited License.

 Texas Instruments Incorporated grants a world-wide, royalty-free,
 non-exclusive license under copyrights and patents it now or hereafter
 owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
 this software subject to the terms herein.  With respect to the foregoing patent
 license, such license is granted  solely to the extent that any such patent is necessary
 to Utilize the software alone.  The patent license shall not apply to any combinations which
 include this software, other than combinations with devices manufactured by or for TI ('TI Devices').
 No hardware patent is licensed hereunder.

 Redistributions must preserve existing copyright notices and reproduce this license (including the
 above copyright notice and the disclaimer and (if applicable) source code license limitations below)
 in the documentation and/or other materials provided with the distribution

 Redistribution and use in binary form, without modification, are permitted provided that the following
 conditions are met:

 * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
 software provided in binary form.
 * any redistribution and use are licensed by TI for use only with TI Devices.
 * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

 If software source code is provided to you, modification and redistribution of the source code are permitted
 provided that the following conditions are met:

 * any redistribution and use of the source code, including any resulting derivative works, are licensed by
 TI for use only with TI Devices.
 * any redistribution and use of any object code compiled from the source code and any resulting derivative
 works, are licensed by TI for use only with TI Devices.

 Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
 promote products derived from this software without specific prior written permission.

 DISCLAIMER.

 THIS SOFTWARE IS PROVIDED BY TI AND TI'S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL TI AND TI'S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package io.roadsense.roadsenseclient.common;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.text.Html;
import android.util.Log;
import android.widget.CompoundButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.roadsense.roadsenseclient.DataSink;

public class SensorTagMovementProfile extends GenericBluetoothProfile {
	
	public SensorTagMovementProfile(Context con, BluetoothDevice device, BluetoothGattService service, BluetoothLeService controller) {
		super(con,device,service,controller);
		
		List<BluetoothGattCharacteristic> characteristics = this.mBTService.getCharacteristics();
		
		for (BluetoothGattCharacteristic c : characteristics) {
            if (c.getUuid().toString().equals(SensorTagGatt.UUID_MOV_DATA.toString())) {
                this.dataC = c;
            }
            if (c.getUuid().toString().equals(SensorTagGatt.UUID_MOV_CONF.toString())) {
                this.configC = c;
            }
            if (c.getUuid().toString().equals(SensorTagGatt.UUID_MOV_PERI.toString())) {
                this.periodC = c;
            }
        }
	}
	
	public static boolean isCorrectService(BluetoothGattService service) {
		if ((service.getUuid().toString().compareTo(SensorTagGatt.UUID_MOV_SERV.toString())) == 0) {
			return true;
		}
		else return false;
	}
	@Override
	public void enableService() {
        byte b[] = new byte[] {0x7F,0x00};
        int error = mBTLeService.writeCharacteristic(this.configC, b);
        if (error != 0) {
            if (this.configC != null)
            Log.d("SensorTagMovementProfile", "Sensor config failed: " + this.configC.getUuid().toString() + " Error: " + error);
        }
        error = this.mBTLeService.setCharacteristicNotification(this.dataC, true);
        if (error != 0) {
            if (this.dataC != null)
            Log.d("SensorTagMovementProfile", "Sensor notification enable failed: " + this.configC.getUuid().toString() + " Error: " + error);
        }

		this.periodWasUpdated(100);
        this.isEnabled = true;
	}
	@Override
	public void disableService() {
        int error = mBTLeService.writeCharacteristic(this.configC, new byte[] {0x00,0x00});
        if (error != 0) {
            if (this.configC != null)
            Log.d("SensorTagMovementProfile", "Sensor config failed: " + this.configC.getUuid().toString() + " Error: " + error);
        }
        error = this.mBTLeService.setCharacteristicNotification(this.dataC, false);
        if (error != 0) {
            if (this.dataC != null)
            Log.d("SensorTagMovementProfile", "Sensor notification disable failed: " + this.configC.getUuid().toString() + " Error: " + error);
        }
        this.isEnabled = false;
	}
	public void didWriteValueForCharacteristic(BluetoothGattCharacteristic c) {
		
	}
	public void didReadValueForCharacteristic(BluetoothGattCharacteristic c) {
		
	}
	@Override
    public void didUpdateValueForCharacteristic(BluetoothGattCharacteristic c) {
        byte[] value = c.getValue();
        if(value == null)
        {
            Log.e("ACC LOGGER", "Received NULL data.");
            return;
        }
        if (c.equals(this.dataC)){
            Point3D v;
            v = Sensor.MOVEMENT_ACC.convert(value);
            //Log.d("ACC LOGGER", "x: " + v.x + "; y: " + v.y + "; z: " + v.z);
            DataSink.Log(v, this.mBTDevice.getAddress());
        }
	}

    public Point3D getData() {
        Point3D v = Sensor.MOVEMENT_ACC.convert(this.dataC.getValue());
        /*Map<String,String> map = new HashMap<String, String>();
        map.put("acc_x", String.format("%.2f", v.x));
        map.put("acc_y", String.format("%.2f", v.y));
        map.put("acc_z", String.format("%.2f", v.z));
        v = Sensor.MOVEMENT_GYRO.convert(this.dataC.getValue());
        map.put("gyro_x", String.format("%.2f", v.x));
        map.put("gyro_y", String.format("%.2f", v.y));
        map.put("gyro_z", String.format("%.2f", v.z));
        v = Sensor.MOVEMENT_MAG.convert(this.dataC.getValue());
        map.put("compass_x", String.format("%.2f", v.x));
        map.put("compass_y", String.format("%.2f", v.y));
        map.put("compass_z", String.format("%.2f", v.z));
        return map;*/
        return v;
    }
}
