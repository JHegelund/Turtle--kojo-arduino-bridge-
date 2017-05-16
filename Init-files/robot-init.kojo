/*
 * Copyright (C) 2014 Lalit Pant <pant.lalit@gmail.com>
 *
 * The contents of this file are subject to the GNU General Public License
 * Version 3 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.gnu.org/copyleft/gpl.html
 */

import jssc.SerialPortList
import jssc.SerialPort
import jssc.SerialPortEventListener
import jssc.SerialPortEvent
import concurrent.Promise
import concurrent.Future
import concurrent.Await
import concurrent.duration._
import java.nio.ByteBuffer
import java.util.prefs.Preferences
import java.util.Date

clearOutput()
@volatile var serialPort: SerialPort = _
@volatile var bytePromise: Promise[Byte] = _
@volatile var intPromise: Promise[Int] = _

def writeArray(arr: Array[Byte]) {
    debugMsg(s"Arduino <- ${arr.toList}")
    arr.foreach { b =>
        var written = false
        do {
            written = serialPort.writeByte(b)
        } while (written == false)
    }
}

// write out an arduino unsigned int
val intArray = new Array[Byte](2)
def writeInt(i: Int) {
    intArray(0) = (i & 0x00FF).toByte
    intArray(1) = (i >> 8).toByte
    writeArray(intArray)
}

def awaitResult[T](f: Future[T]): T = {
    Await.result(f, 5.seconds)
}

val debug = false
def debugMsg(msg: => String) {
    if (debug) {
        println(msg)
    }
}

class SerialPortReader extends SerialPortEventListener {
    //    var currPacket: ByteBuffer = _
    var currData = ByteBuffer.allocate(0)
    var state = 1 // new packet
    var packetSize = 0
    var bytesAvailable = 0

    def readByte: Byte = {
        currData.get
    }

    def readInt: Int = {
        val lowByte: Int = readByte & 0x00FF
        val hiByte: Int = readByte & 0x00FF
        hiByte << 8 | lowByte
    }

    def readString: String = {
        val buf = new Array[Byte](packetSize - 3)
        currData.get(buf)
        new String(buf)
    }

    def serialEvent(event: SerialPortEvent) = synchronized {
        if (event.isRXCHAR && event.getEventValue > 0) { //If data is available
            val data = serialPort.readBytes(event.getEventValue)
            debugMsg(s"Arduino -> ${data.toList}")
            if (currData.hasRemaining) {
                val combinedData = ByteBuffer.allocate(currData.remaining + data.length)
                combinedData.put(currData)
                combinedData.put(data)
                currData = combinedData
                currData.flip()
            }
            else {
                currData = ByteBuffer.wrap(data)
            }
            handleData0()
        }
    }

    def handleData0() {
        state match {
            case 1 =>
                packetSize = currData.get
                bytesAvailable = currData.limit - currData.position
                state = 2
                handleData()
            case 2 =>
                bytesAvailable = currData.limit - currData.position
                handleData()
        }
    }

    def handleData() {
        debugMsg(s"  Bytes available: $bytesAvailable, Curr packet size: $packetSize")
        if (bytesAvailable >= packetSize) {
            readByte match {
                case 1 => // byte
                    readByte; readByte
                    bytePromise.success(readByte)
                case 2 => // int
                    readByte; readByte
                    intPromise.success(readInt)
                case 3 => // string
                    readByte; readByte
                    val msg = readString
                    println(s"[Arduino-Log] $msg")
            }
            packetDone()
        }
    }

    def packetDone() {
        state = 1
        if (currData.hasRemaining) {
            handleData0()
        }
    }
}

import language.implicitConversions
implicit def i2b(i: Int) = i.toByte

runInBackground {
    def connect(portName: String) {
        serialPort = new SerialPort(portName)
        println(s"Opening port: $portName (and resetting Arduino board)...")
        serialPort.openPort()
        serialPort.setParams(SerialPort.BAUDRATE_115200,
            SerialPort.DATABITS_8,
            SerialPort.STOPBITS_1,
            SerialPort.PARITY_NONE,
            true,
            true)
        serialPort.addEventListener(new SerialPortReader())
    }

    def ping(): Boolean = {
        val command = Array[Byte](2, 0, 1)
        intPromise = Promise()
        try {
            writeArray(command)
            val ret = awaitResult(intPromise.future)
            if (ret == 0xF0F0) true else false
        }
        catch {
            case e: Exception =>
                false
        }
    }

    def connectAndCheck(portName: String): Boolean = {
        connect(portName)
        pause(2)
        val good = ping()
        if (!good) {
            serialPort.closePort()
        }
        good
    }

    var arduinoPort: Option[String] = None
    val prefs = builtins.kojoCtx.asInstanceOf[net.kogics.kojo.lite.KojoCtx].prefs
    val knownPort = prefs.get("arduino.port", null)
    if (knownPort != null) {
        println(s"Last successful connection to: $knownPort")
        try {
            val good = connectAndCheck(knownPort)
            if (good) {
                arduinoPort = Some(knownPort)
            }
        }
        catch {
            case t: Throwable =>
                println(s"Problem connecting to last used port: ${t.getMessage}\n")
        }
    }
    if (!arduinoPort.isDefined) {
        val names = SerialPortList.getPortNames
        println(s"Available Ports: ${names.toList}")
        arduinoPort = names.find { portName =>
            val good = connectAndCheck(portName)
            if (good) {
                prefs.put("arduino.port", portName)
            }
            else {
                println(s"Port does not have the Kojo-Arduino bridge running at the other end: $portName")
            }
            good
        }
    }
    if (!arduinoPort.isDefined) {
        throw new RuntimeException("Unable to find an Arduino port with the Kojo-Arduino bridge running at the other end.")
    }

    setRefreshRate(1)
    animate {
    }

    onAnimationStop {
        println(s"Closing port: ${arduinoPort.get}")
        println(s"Stopped at: ${new Date}")
        serialPort.closePort()
    }

    println(s"Started at: ${new Date}")
    println("--")
    setup()
    repeatWhile(true) {
        // thread is interrupted when stop button is pressed
        loop()
    }
}

// API

def pinMode(pin: Byte, mode: Byte) {
    // INPUT - 0; OUTPUT - 1
    val command = Array[Byte](4, 1, 1, pin, mode)
    //                        sz,ns,cmd,arg1,arg2
    writeArray(command)
}

def digitalWrite(pin: Byte, value: Byte) {
    // LOW - 0; HIGH - 1
    val command = Array[Byte](4, 1, 2, pin, value)
    //                        sz,ns,cmd,arg1,arg2
    writeArray(command)
}

def digitalRead(pin: Byte): Byte = {
    val command = Array[Byte](3, 1, 3, pin)
    //                        sz,ns,cmd,arg1
    bytePromise = Promise()
    writeArray(command)
    awaitResult(bytePromise.future)
}

def analogWrite(pin: Byte, value: Int) {
    val command = Array[Byte](4, 1, 7, pin, value.toByte)
    //                        sz,ns,cmd,arg1,arg2
    writeArray(command)
}

def analogRead(pin: Byte): Int = {
    val command = Array[Byte](3, 1, 4, pin)
    //                        sz,ns,cmd, arg1
    intPromise = Promise()
    writeArray(command)
    awaitResult(intPromise.future)
}

def tone(pin: Byte, freq: Int) {
    writeArray(Array[Byte](5, 1, 5, pin))
    writeInt(freq)
}

def tone(pin: Byte, freq: Int, duration: Int) {
    writeArray(Array[Byte](7, 1, 8, pin))
    writeInt(freq)
    writeInt(duration)
}

def noTone(pin: Byte) {
    writeArray(Array[Byte](3, 1, 6, pin))
}

object Servo {
    // proxy for servo library
    // namespace (ns) = 2
    def attach(pin: Byte) {
        val command = Array[Byte](3, 2, 1, pin)
        //                        sz,ns,cmd,arg1
        writeArray(command)
    }
    def attach1(pin: Byte) {
        val command = Array[Byte](3, 2, 3, pin)
        //                        sz,ns,cmd,arg1
        writeArray(command)
    }
    def attach2(pin: Byte) {
        val command = Array[Byte](3, 2, 5, pin)
        //                        sz,ns,cmd,arg1
        writeArray(command)
    }
    def attach3(pin: Byte) {
        val command = Array[Byte](3, 2, 7, pin)
        //                        sz,ns,cmd,arg1
        writeArray(command)
    }
    def write(angle: Int) {
        val command = Array[Byte](3, 2, 2, angle.toByte)
        //                        sz,ns,cmd,arg1
        writeArray(command)
    }
    def write1(angle: Int) {
        val command = Array[Byte](3, 2, 4, angle.toByte)
        //                        sz,ns,cmd,arg1
        writeArray(command)
    }
     def write2(angle: Int) {
        val command = Array[Byte](3, 2, 6, angle.toByte)
        //                        sz,ns,cmd,arg1
        writeArray(command)
    }
    def write3(angle: Int) {
        val command = Array[Byte](3, 2, 8, angle.toByte)
        //                        sz,ns,cmd,arg1
        writeArray(command)
    }
}



val INPUT, LOW = 0.toByte
val OUTPUT, HIGH = 1.toByte

def delay(n: Int) = Thread.sleep(n)
def millis = epochTimeMillis


 
// Rotation value
      var k = 100/9

      // Forward value
      var l = 40

      // Grab'n'release value
      var m = 500

      //Sensor setup

      var sensorPin0 = 0
      var sensorValue0 = 0

      var sensorPin1 = 1
      var sensorValue1 = 0

      var sensorPin2 = 1
      var sensorValue2 = 0

      def setup(){

         // Left motor
         Servo.attach(9)
         Servo.write(90)

         // Right motor
         Servo.attach1(10)
         Servo.write1(90)

         // Upper claw
         Servo.attach2(6)
         Servo.write2(90)

         // Pen
         Servo.attach3(3)
         Servo.write3(90)

      }

      def loop(){


           //println(sensorValue0)
           // println(sensorValue1)
           // println(sensorValue2)
      }



object robot {

   
      def hej{

      }


      def command(cmd : String){
          var indexChar = cmd.indexOf(")")
          
          // Make a left turn

          if(cmd.contains("left")){
            var actionLength = cmd.slice(5, indexChar)
            var actionLengthInput = actionLength.toInt
                                                                   
            Servo.write(180)
            Servo.write1(180) 
            delay(k * actionLengthInput);                      
            
            Servo.write(90)
            Servo.write1(90)
            }

          // Go forward

          else if(cmd.contains("forward")){

            var actionLength = cmd.slice(8, indexChar)
            var actionLengthInput = actionLength.toInt

            Servo.write(0)
            Servo.write1(180)
            delay(l * actionLengthInput)
            Servo.write(90)
            Servo.write1(90)
          }

          // Make a right turn

          else if(cmd.contains("right")){
            var actionLength = cmd.slice(6, indexChar)
            var actionLengthInput = actionLength.toInt

                                       
            Servo.write(0)
            Servo.write1(0) 
            delay(k * actionLengthInput);                       
            
            Servo.write(90)
            Servo.write1(90)
           
          }

          // Make a jump

          else if(cmd.contains("hop")){
            var actionLength = cmd.slice(4, indexChar)
            var actionLengthInput = actionLength.toInt

            Servo.write(0)
            Servo.write1(180)
            Servo.write3(180)

            delay(l * actionLengthInput)
            Servo.write(90)
            Servo.write1(90)
            Servo.write3(90)
          }

          // Go backwards

          else if(cmd.contains("back")){
            var actionLength = cmd.slice(58, cmd.length())
            actionLength = actionLength.slice(0, actionLength.indexOf(")"))
            var actionLengthInput = actionLength.toInt

         
            Servo.write(180)
            Servo.write1(0)
            Servo.write3(180)
            delay(l * actionLengthInput)
            Servo.write(90)
            Servo.write1(90)
            Servo.write3(90)

          
          }

          // Use claw to grab

          else if(cmd.contains("grab")){
           
            Servo.write2(110)
          }

          // Release claw

           else if(cmd.contains("release")){
          
            Servo.write2(70)
          }
      }

  }


// Change these value to tweak the behavior of the buttons
val fdStep = 50
val fdStep2 = 10
val rtStep = 90
val rtStep2 = 10
val bgColor = white
val sBgColor = "white"
var step = 0
var time = 0


   
// End tweak region



clear()
clearOutput()
beamsOn()
val width = canvasBounds.width
val height = canvasBounds.height

setBackground(bgColor)
setPenColor(purple)

def action(code: String) {
  
    new Thread(new Runnable{def run(){ interpret(code); println(code)}
}).start
  new Thread(new Runnable{def run(){ robot.command(code)}
  }).start
    
}

val cmd = Map(
    "forward1" -> s"forward($fdStep)",
    "forward2" -> s"forward($fdStep2)",
    "hop1" -> s"hop($fdStep)",
    "hop2" -> s"hop($fdStep2)",
    "right1" -> s"right($rtStep)",
    "right2" -> s"right($rtStep2)",
    "left1" -> s"left($rtStep)",
    "left2" -> s"left($rtStep2)"
)

def eraseCmds(n: Int) =
    s"saveStyle(); setPenColor($sBgColor); setPenThickness(4); back($n); restoreStyle()"

def button(forcmd: String) = PicShape.button(cmd(forcmd)) { action(cmd(forcmd)) }

val panel = trans(-width / 2, -height / 2) * scale(1.4) -> VPics(
    HPics(
        button("left2"),
        button("forward2"),
        button("right2"),
        button("hop2"),
        PicShape.button(s"erase($fdStep2)") { action(eraseCmds(fdStep2)) },
        PicShape.button(s"grab()") { robot.command(s"grab()"); println(s"grab()") }
        
    ),
    HPics(
        
        button("left1"),
        button("forward1"),
        button("right1"),
        button("hop1"),
        PicShape.button(s"erase($fdStep)") { action(eraseCmds(fdStep)) },
        PicShape.button(s"release()") { robot.command(s"release()"); println(s"release()") }
    )
)

draw(panel)
println("// Paste the generated program below into the script editor")
println("// and run it -- to reproduce your drawing")
println("clear()")



