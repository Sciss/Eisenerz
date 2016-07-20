/*
 *  Accelerate.scala
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

import de.sciss.osc
import de.sciss.synth._

import scala.math.Pi

object Accelerate {
  final case class Config()

  def main(args: Array[String]): Unit = {
    run(Config())
  }

  def run(config: Config): Unit = {
    import scala.sys.process._
    Seq("killall", "scsynth").!

    val sCfg = Server.Config()
    sCfg.transport          = osc.TCP
    sCfg.inputBusChannels   = 1
    sCfg.outputBusChannels  = 2
    sCfg.sampleRate         = sampleRate
    sCfg.blockSize          = smpIncr // ! so we can use decimation through ar -> kr
    sCfg.deviceName         = Some("ScalaCollider")

    println("Booting server...")
    Server.run(sCfg)(serverStarted)
  }

  private[this] val smpIncr         = 32
  private[this] val loopDurMinutes  = 20
  private[this] val sampleRate      = 44100
  private[this] val loopLen         = loopDurMinutes * 60 * sampleRate

  private def serverStarted(s: Server): Unit = {
    println("Server started.")

    val bufKernel = Buffer(s)
    import Ops._
    val kernel    = mkFilterKernel()
    bufKernel.alloc(numFrames = kernel.length, numChannels = 1, completion = bufKernel.setnMsg(kernel))
    val bufLoop   = Buffer(s)
    bufLoop.alloc(numFrames = loopLen, numChannels = 1, completion = bufLoop.zeroMsg)

    play {
      import ugen._
//      val in        = WhiteNoise.ar(0.25)
//      val in        = SinOsc.ar(100) * 0.25
      val in        = PhysicalIn.ar(0)
      val flt       = Convolution2.ar(in = in, kernel = bufKernel.id, frameSize = kernel.length)
      val fltK      = A2K.kr(flt)
      RecordBuf.kr(in = fltK, buf = bufLoop.id, offset = 0, recLevel = 1, preLevel = 0, run = 1, loop = 1, trig = 1)
      val bufValid  = Sweep.ar(trig = 0, speed = sampleRate.toDouble / smpIncr).min(loopLen)
      // val playTrig  = Impulse.ar(1.0/10) // XXX TODO
      val playTrig  = Impulse.ar(sampleRate / bufValid.max(1))
      val sig       = PlayBuf.ar(numChannels = 1, buf = bufLoop.id, speed = 1.0, trig = playTrig, loop = 1)

      Out.ar(0, Pan2.ar(sig))
    }
  }

  // ---- dsp ----

  private def mkFilterKernel(): Array[Float] = {
    val rollOff           = 0.8
    val kaiserBeta        = 7.0
    val zeroCrossings     = 9
    val fltSmpPerCrossing = 4096
    val fltLen            = ((fltSmpPerCrossing * zeroCrossings) / rollOff + 0.5).toInt
    // val factor            = 1.0 / smpIncr
    require(fltSmpPerCrossing % smpIncr == 0)
    val fltIncr           = fltSmpPerCrossing / smpIncr
    val fltLen1           = fltLen / fltIncr
    val impResp           = new Array[Float](fltLen1)
    assert(fltLen1 == 360)
    createAntiAliasFilter(impResp = impResp, halfWinSize = fltLen1, rollOff = rollOff, kaiserBeta = kaiserBeta,
      samplesPerCrossing = fltSmpPerCrossing, step = fltIncr)
    val fullSize          = fltLen1 + fltLen1 - 1
    val fftSizeH          = fullSize.nextPowerOfTwo
    assert(fftSizeH == 1024)
    val res               = new Array[Float](fftSizeH)
    var i = 0
    while (i < fltLen1) {
      val x = impResp(i)
      res(fltLen1 - 1 - i) = x
      res(fltLen1 - 1 + i) = x
      i += 1
    }
    res
  }

  /* @param	impResp				  Ziel-Array der Groesse 'halfWinSize' fuer Impulsantwort
   * @param	freq				    Grenzfrequenz
   * @param	halfWinSize			Groesse des Kaiser-Fensters geteilt durch zwei
   * @param	kaiserBeta			Parameter fuer Kaiser-Fenster
   * @param	samplesPerCrossing	Zahl der Koeffizienten pro Periode
   */
  private def createLPF(impResp: Array[Float], freq: Double, halfWinSize: Int, kaiserBeta: Double,
                        samplesPerCrossing: Int, step: Int): Unit = {
    val dNum        = samplesPerCrossing.toDouble
    val smpRate     = freq * 2.0
    val normFactor  = 1.0 / (halfWinSize - 1)

    // ideal lpf = infinite sinc-function; create truncated version
    impResp(0) = smpRate.toFloat
    var i = 1
    while (i < halfWinSize) {
      val d = Pi * i / dNum
      impResp(i) = (math.sin(smpRate * d) / d).toFloat
      i += step // 1
    }

    // apply Kaiser window
    val iBeta = 1.0 / calcBesselZero(kaiserBeta)
    i = 1
    while (i < halfWinSize) {
      val d = i * normFactor
      impResp(i) *= (calcBesselZero(kaiserBeta * math.sqrt(1.0 - d * d)) * iBeta).toFloat
      i += step // 1
    }
  }

  private def calcBesselZero(x: Double): Double = {
    var d2  = 1.0
    var sum = 1.0
    var n   = 1
    val xh  = x * 0.5

    do {
      val d1 = xh / n
      n += 1
      d2 *= d1 * d1
      sum += d2
    } while (d2 >= sum * 1e-21) // precision is 20 decimal digits

    sum
  }

  private def createAntiAliasFilter(impResp: Array[Float], halfWinSize: Int, rollOff: Double,
                                    kaiserBeta: Double, samplesPerCrossing: Int, step: Int): Unit =
    createLPF(impResp, 0.5 * rollOff, halfWinSize = halfWinSize, kaiserBeta = kaiserBeta,
      samplesPerCrossing = samplesPerCrossing, step = step)
}