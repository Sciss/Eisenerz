/*
 *  Exposure.scala
 *  (Eisenerz)
 *
 *  Copyright (c) 2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenerz

import com.pi4j.io.gpio.{GpioFactory, PinMode, PinPullResistance, PinState, RaspiPin}

import scala.annotation.tailrec

object Exposure {
  def main(args: Array[String]): Unit = {
    println("Running 'Key Matrix'...")
    keyMatrix()
  }

  def keyMatrix(): Unit = {
    val NotPressed = 'X'

    val KeyPad = Array(
      Array('1', '2', '3'),
      Array('4', '5', '6'),
      Array('7', '8', '9')
    )

    import RaspiPin._

    // so we use GPIO_00 and GPIO_01 for the LED (could drop one)

    val rows    = Array(GPIO_07, GPIO_02, GPIO_03)  // 18,23,24,25
    val columns = Array(GPIO_04, GPIO_05, GPIO_06)  // 4,17,22

    val io      = GpioFactory.getInstance

    val rowPins = rows   .map(pin => io.provisionDigitalMultipurposePin(pin, PinMode.DIGITAL_INPUT, PinPullResistance.PULL_UP))
    val colPins = columns.map(pin => io.provisionDigitalMultipurposePin(pin, PinMode.DIGITAL_OUTPUT))

    // Reinitialize all rows and columns as input at exit
    (rowPins ++ colPins).foreach(_.setShutdownOptions(true, PinState.LOW, PinPullResistance.PULL_UP, PinMode.DIGITAL_INPUT))

    def readKey(): Char = {
      // Set all columns as output low
      colPins.foreach { pin =>
        pin.setMode(PinMode.DIGITAL_OUTPUT)
        pin.low()
      }

      // Set all rows as input
      rowPins.foreach { pin =>
        pin.setMode(PinMode.DIGITAL_INPUT)
        pin.setPullResistance(PinPullResistance.PULL_UP)
      }

      // Scan rows for pushed key/button
      // A valid key press should set "rowVal"  between 0 and 3.
      val rowIdx = rowPins.indexWhere(_.isLow)

      // if rowIdx is not in 0 to 2 then no button was pressed and we can exit
      if (rowIdx < 0) return NotPressed

      // Convert columns to input
      colPins.foreach { pin =>
        pin.setMode(PinMode.DIGITAL_INPUT)
        pin.setPullResistance(PinPullResistance.PULL_DOWN)
      }

      // Switch the i-th row found from scan to output
      val rowPin = rowPins(rowIdx)
      rowPin.setMode(PinMode.DIGITAL_OUTPUT)
      rowPin.high()

      // Scan columns for still-pushed key/button
      // A valid key press should set "colIdx"  between 0 and 2.
      val colIdx = colPins.indexWhere(_.isHigh)

      // if colIdx is not in 0 to 2 then no button was pressed and we can exit
      if (colIdx < 0) return NotPressed

      KeyPad(rowIdx)(colIdx)
    }

    @tailrec def loop(): Char = {
      val c = readKey()
      if (c == NotPressed) loop() else c
    }

    val res = loop()
    println(s"Key pressed: $res")
  }

  // XXX TODO --- use pwm, so we can balance red and green intensity for orange mix
  def dualColorLED(): Unit = {
    val io    = GpioFactory.getInstance
    val pinR  = io.provisionDigitalOutputPin(RaspiPin.GPIO_00, "Red"  , PinState.HIGH)
    val pinG  = io.provisionDigitalOutputPin(RaspiPin.GPIO_01, "Green", PinState.LOW )
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

    io.shutdown()
  }
}
