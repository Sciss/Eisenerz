package de.sciss.eisenerz

import com.pi4j.io.gpio.{GpioFactory, PinState, RaspiPin}

object Exposure {
  def main(args: Array[String]): Unit = {
    val gpio = GpioFactory.getInstance
    val pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "Red", PinState.HIGH)
    pin.setShutdownOptions(true, PinState.LOW)

    def infoWait(b: Boolean): Unit = {
      println("--> GPIO state should be: ON")
      Thread.sleep(3000)
    }

    infoWait(true)
    pin.low()
    infoWait(false)
    pin.toggle()
    infoWait(true)
    pin.toggle()
    infoWait(false)
    println("--> GPIO state should be: ON for only 1 second")
    pin.pulse(1000, true)  // true = blocking

    gpio.shutdown()
  }
}
