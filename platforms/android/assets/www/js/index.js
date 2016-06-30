/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

 /*MiBand Plugin*/
 window.synchronizeBand = function(onSuccess, onError) {
     cordova.exec(onSuccess,onError, "MiBandPlugin", "synchronizeBand", []);
 };

 window.connectBand = function(onSuccess, onError) {
      cordova.exec(onSuccess,onError, "MiBandPlugin", "connectBand", []);
 };

 window.getLiveStepCount = function(onSuccess, onError) {
      cordova.exec(onSuccess,onError, "MiBandPlugin", "getLiveStepCount", []);
 };

 window.getBatteryInfo=function(onSuccess, onError) {
     cordova.exec(onSuccess,onError, "MiBandPlugin", "getBatteryInfo", []);
 };


var app = {
    // Application Constructor
    initialize: function() {
        this.bindEvents();
    },
    // Bind Event Listeners
    //
    // Bind any events that are required on startup. Common events are:
    // 'load', 'deviceready', 'offline', and 'online'.
    bindEvents: function() {
        document.addEventListener('deviceready', this.onDeviceReady, false);
    },
    // deviceready Event Handler
    //
    // The scope of 'this' is the event. In order to call the 'receivedEvent'
    // function, we must explicitly call 'app.receivedEvent(...);'
    onDeviceReady: function() {
        app.receivedEvent('deviceready');
        updateView();
    },
    // Update DOM on a Received Event
    receivedEvent: function(id) {
        console.log('Received Event: ' + id);
    }
};

app.initialize();

var connected=false;
var out="";
var steps=0;

function updateView(){
    if (connected){
        document.getElementById("connectionStatus").innerHTML="<b style='color:green'>VERBUNDEN MIT MI BAND</b>";
    }else{
        document.getElementById("connectionStatus").innerHTML="<b style='color:red'>NICHT VERBUNDEN</b>";
    }

    document.getElementById("out").innerHTML=document.getElementById("out").innerHTML+"<br><br>"+out;
    document.getElementById("steps").innerHTML=steps;
}

function synchronizeBandNow(){
    setLoading(true);
    if (!connected){setLoading(false);alert("Nicht verbunden!");return;}
    window.synchronizeBand(function(data){
        steps=data.msg;
        setLoading(false);
        out="Synchronization successfully completed: "+steps;
        updateView();
    }, function(error){
        out=error.msg;
        setLoading(false);
        updateView();
    });
}

function setLoading(load){
    if(load){
        document.getElementById("loading").style.display="block";
    }else{
       document.getElementById("loading").style.display="none";
    }
}

function connect(){
    setLoading(true);
    window.connectBand(function(data){
        connected=true;
        out=data.msg;
        updateView();
        setLoading(false);
    }, function(error){
        connected=false;
        out =error.msg;
        updateView();
        setLoading(false);
    });
}

function getCurrentStepCount(){
    setLoading(true);
    if (!connected){setLoading(false);alert("Nicht verbunden!");return;}
    window.getLiveStepCount(function(data){
        steps=data.msg;
        setLoading(false);
        out="Fetch live steps successfully completed: "+steps;
        updateView();
    },function(error){
        out=error.msg;
        setLoading(false);
        updateView();
    });
}

function getBatteryInfos(){
    setLoading(true);
    if (!connected){setLoading(false);alert("Nicht verbunden!");return;}
    window.getBatteryInfo(function(data){
        out="Get Battery successfully completed: "+data.msg;
        setLoading(false);
        updateView();
    }, function(error){
        out=error.msg;
        setLoading(false);
        updateView();
    });
}

document.getElementById("connectBtn").addEventListener("click", connect);
document.getElementById("liveBtn").addEventListener("click", getCurrentStepCount);
document.getElementById("syncBtn").addEventListener("click", synchronizeBandNow);
document.getElementById("getBatBtn").addEventListener("click", getBatteryInfos);