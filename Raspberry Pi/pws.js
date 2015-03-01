var app = require('express')();
var http = require('http').Server(app);
var io = require('socket.io')(http);
var serialport = require("serialport");

var SerialPort = serialport.SerialPort;

var commandSetSetpoint = 'e';
var commandSetSettings = 'c';
var commandUpdateTauKp = 'd';
var commandSetCalibratedHoek = 'f';
var commandSetMode = 'g';

var arduinoConnected = true;

if (arduinoConnected){
	var serialPort = new SerialPort("/dev/ttyACM0", {
	  baudrate: 115200,
	  dataBits: 8,
	  parity: 'none',
	  stopBits: 1,
	 flowControl: false,
	 parser: serialport.parsers.readline('\r\n')
	});
}
http.listen(3000, function(){
  console.log('listening on *:3000');
});

app.get('/', function(req, res){
  res.sendFile(__dirname + '/index.html');
});

if (arduinoConnected){
	serialPort.on("open", function () {
	  console.log('serialport has made connnection');

	  //wannneer je data ontvangt
	  serialPort.on('data', function(data) {
	      //result = data.trim();
	      console.log('data received: ' + data);

	      var arr = data.split(" ");

	      switch(arr[0]){
	      	case "a":
	      	//status
	      	var msg = {a:arr[1], o:arr[2], i:arr[3], p:arr[4]};

	      	io.emit("stat", msg);
	      	break;

	      	default:
	      	break;
	      };
	  });

	  //wanneer er een error is..
	  serialPort.on('error', function (err) {
	      console.error("error", err);
	  });

	});
}

io.on('connection', function(socket){
  socket.emit('server',{msg : 'You are conencted'} );

	socket.on('updateTauKp', function(msg){	
		var tau = parseInt(msg.tau,10);
		var kp = parseInt(msg.kp,10);
		
		pushStringToArduino(commandUpdateTauKp + " " + tau+" "+kp+"\n");
	});

  socket.on('setpoint', function(msg){	
		var y = msg.deltaY;

		y = parseFloat(y,10);
		
		pushStringToArduino(commandSetSetpoint + " " + y + "\n");
	});

	socket.on('hoek', function(msg){
		var hoek = msg.hoek;

		pushStringToArduino(commandSetCalibratedHoek + " " + hoek +"\n");
  });

  	socket.on('mode', function(msg){
		var mode = msg.mode;

		pushStringToArduino(commandSetMode + " " + mode +"\n");
  });

  socket.on('start', function(msg){
		var startCode = msg.startCode;
		var tau = parseInt(msg.tau,10);
		var kp = parseInt(msg.kp,10);

		pushStringToArduino(commandSetSettings + " " + startCode + " "+tau+" "+kp+"\n");
		
  });

});

var pushStringToArduino = function(msg){

	console.log(msg);

	if (arduinoConnected){
			serialPort.write(msg, function(err, results) {
			  if(err != null){
			  	console.log('results ' + results + " err : "+err);
			  }
			});
		}

}