#include <ADXL345.h>
#include <bma180.h>
#include <HMC58X3.h>
#include <ITG3200.h>
#include <MS561101BA.h>
#include <I2Cdev_freeimu.h>
#include <MPU60X0.h>
#include <EEPROM.h>
//http://192.168.2.9:3000 -> raspberry


//#define DEBUG
#include "DebugUtils.h"
#include "CommunicationUtils.h"
#include "FreeIMU.h"
#include <Wire.h>
#include <SPI.h>

float inputAngle;
float inputAngleOld;
float inputAngleOffset;
float ypr[3]; // yaw pitch roll

//program variables
const int accX = 0;         //Acceleration senosor pins
const int motorIn = 11;
const int IRSensor = 4;
const int calButton = 8;
const int stopButton = 12;
const int calibrationLed = 7;
const int startProgramLed = 5;
const int pinSTartProgramWithoputRaspberry = A2;
/*
maxLimit is calibrated to 184
minLimit is calibrated to 70
*/
const int maxLimit = 184; 
const int minLimit = 70;
const int motorOffSet = 127;     //

const int deltaT = 20;
float tau = 0.07;    //0.25
float kp = 1.8;      //0.2

const int startTime = 2500;

boolean startTest = false;
boolean start = true;
boolean isCalibrated = false;
boolean isCalibratedAndWaited = false;
boolean willCalibrate = false;

int sensorOffSet;
int sensorX;
int conFilterOut;
int out;

float setPoint;
float current_setpoint = 0;

int time;
int setTime;

float aSensor;              //Amplification sensor signal
float present;              //Feedback signal
float eps;
float prop;
float integral;
float filterOut;

//Times
const long timeBetweenCalibrationAndStart = 2500; // ms
long interval = 1000 / 10;
long previousMillis = 0; 
long timeWaitBetweenAngles = 0;
long timeOfCalibration;
long timeLastTimerForLoopFreq;

FreeIMU sensor = FreeIMU();

int activeSensor = 1; //1 voor mpu6050, 2 voor versnellingssensor

void setup(){  
  Serial.begin(115200);
  Wire.begin();
  
  delay(5);
  sensor.init(); // the parameter enable or disable fast mode
  delay(5);
  
  pinMode(calButton, INPUT);
      pinMode(stopButton, INPUT);
      pinMode(IRSensor, INPUT);
      pinMode(calibrationLed, OUTPUT);
      pinMode(startProgramLed, OUTPUT);
      
      out = motorOffSet;
      analogWrite(motorIn, out);
      aSensor = 1;
      integral = 0;
      setPoint = 0;
  
}

void loop() {  
  checkIfWeNeedToStop();
  
  // == LOW is for Tom, == HIGH for Egbert
  if (digitalRead(calButton) == LOW){
      willCalibrate = true;
      Serial.println(F("b calib_button_pressed"));
      digitalWrite(calibrationLed, HIGH);
  }
  
  /*if (digitalRead(stopButton) == LOW){
    startTest = true;
    tau = 0.07;
    kp = 1.8;
  }*/
  
  if(activeSensor == 1){
    getAngleMpu();
  }else if(activeSensor == 2){
    getAngleVersnellingsSensor();
  }
  
  checkForDataFromPi();
  
  if (isCalibrated && startTest){
    digitalWrite(startProgramLed, HIGH);
     digitalWrite(calibrationLed, LOW);
     
     if(activeSensor == 1){
        doCalcMpu();
      }else if(activeSensor == 2){
        doCalcVersnellingssensor();
      }
  }
  
  
  long currentMillis = millis();
  //10HZ loop
  if(currentMillis - previousMillis > interval) {
   previousMillis = currentMillis; 
   
   Serial.print(F("a "));
   Serial.print(inputAngle);
   Serial.print(F(" "));
   Serial.print(out);
   Serial.print(F(" "));
   Serial.print(integral);
   Serial.print(F(" "));
   Serial.println(prop);
   
   //kijk nu ook naar setpoint en verander eventueel:
   if(setPoint > current_setpoint){
     current_setpoint += 0.1;     
   }else if(setPoint < current_setpoint){
     current_setpoint -= 0.1;     
   }
  }
  
    if((currentMillis - timeLastTimerForLoopFreq ) < deltaT){
      delay(deltaT - (currentMillis - timeLastTimerForLoopFreq));
    }
    
    timeLastTimerForLoopFreq = millis();
  
}


int checkOutput(int out, int maxLimit, int minLimit){
  if (out >= maxLimit){
    out = maxLimit;
  }
  if (out <= minLimit){
    out = minLimit;
  }
  return out;
}

//---------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------
void checkForDataFromPi(){
   while (Serial.available() > 0) {
       char incommingCommand = Serial.read();
       
       if(incommingCommand == 'e'){
         //beweging voor/achter uit
         int newSetpoint = Serial.parseInt();
         
         setPoint = newSetpoint / 10.0f;
         
         setPoint = newSetpoint;
       }else if(incommingCommand == 'c'){
         //start/stop
         int startCode = Serial.parseInt();
         int tauInt = Serial.parseInt();
         int kpInt = Serial.parseInt(); 
         
         tau = tauInt / 100.0f;
         kp = kpInt / 100.0f;
         
         
         if (Serial.read() == '\n') {
           if(startCode == 1){
             startTest = true;
           }else if(startCode == 0){
             startTest = false;
             STOP();
           }
         }
       }else if(incommingCommand == 'd'){
         int tauInt = Serial.parseInt();
         int kpInt = Serial.parseInt(); 
         
         tau = tauInt / 100.0f;
         kp = kpInt / 100.0f;
       }else if(incommingCommand == 'f'){
         int mHoek = Serial.parseInt();
         
         inputAngleOffset = mHoek / 10.0f;
       }else if(incommingCommand == 'g'){
         int mode = Serial.parseInt();
         
         switch(mode){
           case 1:
           //rechtop_mpu
             aSensor = 1;
             activeSensor = 1;
           break;
           case 2:
           //rechtop_versnellingssensor
             aSensor = 1;
             setPoint = 0;
             activeSensor = 2;
           break;
           case 3:
           //onder_mpu
            aSensor = -1;
            activeSensor = 1;
           break;
           case 4:
           //onder_versnelling
             aSensor = -1;
             setPoint = 0;
             activeSensor = 2;
           break;
         }
       }
     }
}
//---------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------

void STOP(){
  startTest = false;
  isCalibrated = false;
  Serial.println(F("b STOP"));
    out = motorOffSet;
    integral = 0;
    analogWrite(motorIn, out);
    digitalWrite(startProgramLed, LOW);
}

//---------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------
void checkIfWeNeedToStop(){
  if (digitalRead(IRSensor) == HIGH){
    STOP();
  }
  if (digitalRead(stopButton) == LOW){
    STOP();
  }
}
//---------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------
void doCalcMpu(){
    present = aSensor*(inputAngle-inputAngleOffset);
    eps = current_setpoint - present;
    prop = kp*eps;
    integral = integral + eps * (float(deltaT) / 1000) / tau;
    filterOut = prop + integral;
    conFilterOut = int(filterOut);
    out = motorOffSet - conFilterOut;
    out = checkOutput(out, maxLimit, minLimit);
    analogWrite(motorIn, out);
}
//---------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------
void doCalcVersnellingssensor(){
    present = aSensor*(sensorX-sensorOffSet);
    eps = current_setpoint - present;
    prop = kp*eps;
    integral = integral + eps * (float(deltaT) / 1000) / tau;
    filterOut = prop + integral;
    conFilterOut = int(filterOut);
    out = motorOffSet - conFilterOut;
    out = checkOutput(out, maxLimit, minLimit);
    analogWrite(motorIn, out);
}
//---------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------
void getAngleMpu(){
  sensor.getYawPitchRoll(ypr);
  
  inputAngle = -ypr[2];
  
  if(willCalibrate){
    isCalibrated = true;
    willCalibrate = false;
    //inputAngleOffset = inputAngle;
    inputAngleOffset = 1.2;
    integral = 0;
    sensorOffSet = analogRead(0);
  }
}

void getAngleVersnellingsSensor(){
  sensorX = analogRead(0);
  
   if(willCalibrate){
    isCalibrated = true;
    willCalibrate = false;
    //inputAngleOffset = inputAngle;
    inputAngleOffset = 1.2;
    integral = 0;
    sensorOffSet = analogRead(0);
  }
}
