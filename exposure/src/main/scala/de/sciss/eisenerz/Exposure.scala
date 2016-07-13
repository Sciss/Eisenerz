package de.sciss.eisenerz

import com.pi4j.io.gpio.{GpioFactory, PinState, RaspiPin}

object Exposure {
  def main(args: Array[String]): Unit = {
    val gpio = GpioFactory.getInstance
    val pinR = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, "Red"  , PinState.HIGH)
    val pinG = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "Green", PinState.LOW )
    pinR.setShutdownOptions(true, PinState.LOW)
    pinG.setShutdownOptions(true, PinState.LOW)

    def infoWait(b: Boolean): Unit = {
      println("--> GPIO state should be: ON")
      Thread.sleep(3000)
    }

    infoWait(true)
    pinR.low()
    pinG.high()
    infoWait(false)
    pinR.toggle()
    pinG.toggle()
    infoWait(true)
    pinR.toggle()
    pinG.toggle()
    infoWait(false)
    println("--> GPIO state should be: ON for only 1 second")
    pinR.pulse(1000, true)  // true = blocking

    gpio.shutdown()
  }
}
