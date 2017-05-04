// Rotation value
var k = 100/9

// Forward value
var l = 50

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

     sensorValue0 = analogRead(sensorPin0)
     sensorValue1 = analogRead(sensorPin1)
     sensorValue2 = analogRead(sensorPin2)

     //println(sensorValue0)
     // println(sensorValue1)
     // println(sensorValue2)
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