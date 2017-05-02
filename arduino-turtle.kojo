var k = 100/9
var l = 50

def setup(){
   Servo.attach(9)
   Servo.attach1(10)
   
}

def loop(){
   
}

def command(cmd : String){
    var indexChar = cmd.indexOf(")")
    
    if(cmd.contains("left")){
      var actionLength = cmd.slice(5, indexChar)
      var actionLengthInput = actionLength.toInt

      var pos = 180;                                                             
      Servo.write(pos)
      Servo.write1(pos) 
      delay(k * actionLengthInput);                      
      
      Servo.write(90)
      Servo.write1(90)
      }

    else if(cmd.contains("forward")){
      var actionLength = cmd.slice(8, indexChar)
      var actionLengthInput = actionLength.toInt

      var pos2 = 0
      var pos3 = 180
      Servo.write(pos2)
      Servo.write1(pos3)
      delay(l * actionLengthInput)
      Servo.write(90)
      Servo.write1(90)
    }

    else if(cmd.contains("right")){
      var actionLength = cmd.slice(6, indexChar)
      var actionLengthInput = actionLength.toInt

      var pos = 0;                               
      Servo.write(pos)
      Servo.write1(pos) 
      delay(k * actionLengthInput);                       
      
      Servo.write(90)
      Servo.write1(90)
     
    }
    else if(cmd.contains("hop")){

    }
    else if(cmd.contains("back")){
      var actionLength = cmd.slice(58, cmd.length())
      actionLength = actionLength.slice(0, actionLength.indexOf(")"))
      var actionLengthInput = actionLength.toInt

      var pos2 = 0
      var pos3 = 180
      Servo.write(pos3)
      Servo.write1(pos2)
      delay(l * actionLengthInput)
      Servo.write(90)
      Servo.write1(90)
    
    }
}