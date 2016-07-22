#!/bin/sh
qjackctl &
sleep 5
amixer cset numid=1 0  # 0 dB volume
wpa_cli terminate
sleep 5
cd /home/pi/Documents/devel/Eisenerz/zerophase
java -jar Eisenerz-ZeroPhase.jar -d 480
sleep 10
