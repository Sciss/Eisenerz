#!/bin/sh
# sudo because we need the same user for jackd and scsynth!
sudo qjackctl &
sleep 5
wpa_cli terminate
sleep 5
cd /home/pi/Documents/devel/Eisenerz/accelerate
sudo java -jar Eisenerz-Accelerate.jar
sleep 10
