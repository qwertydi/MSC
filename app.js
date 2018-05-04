const noble = require("noble");
const BeaconScanner = require("node-beacon-scanner");
var _ = require('lodash');
/*noble.on('error', displayError);
noble._bindings.on('error', displayError);
*/
var allowDuplicates = false;

noble.startScanning(['0x180F'], allowDuplicates);

noble.on('stateChange', function (state) {
  console.log('state change', state);
});

noble.on('discover', function (peripheral) {
  //console.log('peripheral found!', peripheral);

  peripheral.connect(function (error) {
    console.log("Connected to device mac: " + peripheral.address);
    console.log("Advertisement info: " + peripheral.advertisement.localName);
    console.log("ID info: " + peripheral.id);
    console.log("UUID info: " + peripheral.uuid);
    if (error) {
      //displayError(error);
      return console.error('error connecting to peripheral', error);
    }
    console.log('connected to peripheral', peripheral);
    peripheral.discoverServices(['180f'], function(error, services) {
      var deviceInformationService = services[0];
      console.log("service: " + services[0]);
    });

    peripheral.on('servicesDiscover', function (services) {
      global.services = '0x180F';


      services.forEach(function (service) {
        service.on('characteristicsDiscover', function (characteristics) {
          //console.log(characteristics);
          //console.log('characteristics Discovered', characteristics);

          characteristics.forEach(function (characteristic) {
              _.forEach(characteristic.properties, function(prop){

                if (!characteristic.subbed) {
                  characteristic.subbed = true;
                  characteristic.subscribe(function (error) {
                    if (error) {
                      console.error('error subscribing', error);
                    } else {
                      console.error('subscribed');
                      characteristic.subbed = true;
                    }
                  });
                }
                
                characteristic.on('data', function (data, isNotification) { //tells location of body sensor
                 // console.log('char data', data, isNotification, characteristic.uuid);
                  console.dir(new Buffer(data).toJSON());
                  var value = new Buffer(data).toJSON().data;

                  console.log("test: " + value[0]);
                  //console.log("A porta encontra-se com " + value[0] + " graus aberta");
                  // charValVal.textContent ="A porta encontra-se com " + value[0] + " graus aberta";
                  // charValDate.textContent = " at: " + new Date().toTimeString();
                });

              });
          });

        });
        service.discoverCharacteristics();
      });

    });

  });

  peripheral.discoverServices();


});