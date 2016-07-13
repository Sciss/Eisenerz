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

import com.hopding.jrpicam.RPiCamera
import com.hopding.jrpicam.enums.{AWB, Encoding}
import com.pi4j.io.gpio.GpioFactory
import de.sciss.file._

import scala.annotation.tailrec
import scala.util.Try

object Exposure {
  private[this] val outputDir = file("/") / "media" / "pi" / "exposure" / "Pictures"

  sealed trait State
  case object StatePause    extends State
  case object StateRecord   extends State
  case object StateShutdown extends State

  @volatile
  private[this] var state: State = StatePause

  def main(args: Array[String]): Unit = {
    // keyMatrix()
    // dualColorLED()
    run()
  }

  /* Delay between taking photos in milliseconds. */
  private[this] val Delay = 10000

  def run(): Unit = {
    require(outputDir.isDirectory && outputDir.canWrite, s"Cannot write to $outputDir")

    val keys  = new KeyMatrix
    val led   = new DualColorLED

    val cam = new RPiCamera(outputDir.path)
    // cf. https://raspberrypi.stackexchange.com/questions/14047/
    cam.setShutter(500000)
    cam.setAWB(AWB.OFF)
    val width     = 3280/2
    val height    = 2464/2
    val encoding  = Encoding.JPG
    cam.setEncoding(encoding)
    cam.turnOffPreview()
    // cam.setTimeout()
    val ext = encoding.toString

    led.pulseGreen()  // 'ready'

    // XXX TODO --- this could be slow for lots of pictures; perhaps use 'jumping'
    var count = outputDir.children(_.name.startsWith("frame-")).flatMap { f =>
      val n = f.name
      Try(n.substring(6, n.indexOf('.', 6)).toInt).toOption
    } .sorted.lastOption.getOrElse(0) + 1

    println(s"Next frame will be #$count")

    while (state != StateShutdown) {
      if (state == StateRecord) {
        val name = s"frame-$count.$ext"
        cam.takeStill(name, width, height)
        count += 1
      }
      var dlyRemain = Delay
      while (dlyRemain > 0) {
        Thread.sleep(100)
        keys.read() match {
          case '1' =>
            if (state != StateRecord) {
              state     = StateRecord
              dlyRemain = 0
              led.pulseRed()
            }
          case '2' =>
            if (state != StatePause) {
              state = StatePause
              led.pulseGreen()
            }
          case '9' =>
            state     = StateShutdown
            dlyRemain = 0
            led.blinkRed()
        }
        dlyRemain -= 100
      }
    }

    import scala.sys.process._
    Seq("sudo", "shutdown", "now").!
  }

  def keyMatrix(): Unit = {
    println("Running 'Key Matrix'...")

    val keys  = new KeyMatrix
    val io    = GpioFactory.getInstance

    @tailrec def loop(): Char = {
      val c = keys.read()
      if (c == KeyMatrix.NotPressed) loop() else c
    }

    val res = loop()
    println(s"Key pressed: $res")
    io.shutdown()
  }

  // XXX TODO --- use pwm, so we can balance red and green intensity for orange mix
  def dualColorLED(): Unit = {
    val led   = new DualColorLED
    val io    = GpioFactory.getInstance

    led.red()
    Thread.sleep(2000)
    led.green()
    Thread.sleep(2000)
    io.shutdown()
  }
}