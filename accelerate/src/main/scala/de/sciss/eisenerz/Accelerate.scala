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

import java.io.{PrintStream, RandomAccessFile}

import de.sciss.file._
import de.sciss.osc
import de.sciss.synth._
import de.sciss.synth.io.{AudioFile, AudioFileSpec, AudioFileType, SampleFormat}

import scala.concurrent.ExecutionContext
import scala.math.Pi
import scala.util.control.NonFatal

/*

  Note: 'command FIFO full'
  - might be that jackd actually has a problem and we need to kill it as well
  - and restart qjackctl
  - we try now with wifi disabled

 */
object Accelerate {
  final case class Config(snapshotFile: File = file("/") / "media" / sys.props("user.name") / "accel" /"snapshot.irc",
                          loopDurMinutes: Int = 1 /* 20 */) {
    val loopLen = loopDurMinutes * 60 * sampleRate
  }

  def main(args: Array[String]): Unit = {
    run(Config())
  }

  def restartAfterFailure(): Unit = {
//    import scala.sys.process._
//    Seq("sudo", "reboot", "now").!
  }

  def watchFIFO(): Unit = {
    // Ok, here is the tricky bit:
    // ScalaCollider redirects the output stream
    // of the scsynth process, exclusively calling
    // `println`, so we actually just have to look
    // for the underlying calls to `println(x: Any)`.
    // In the implementation that calls `print(x: String)` followed by newline.
    // So in order to check for message, what we do here is override
    // just `print(x: String)`. We then replace `Console.out`.
    val printStream = new PrintStream(System.out, true) {
      override def print(x: String): Unit = {
//        super.print(s"FOO: $x")
        super.print(x)
        if (x.contains("command FIFO full")) {
          super.print("--- DETECTED!")
          restartAfterFailure()
        }
      }
    }
    Console.setOut(printStream) // yes, deprecated my a**
  }

  def run(config: Config): Unit = {
    import scala.sys.process._
    Seq("killall", "scsynth").!

    // import config._
    // if (!snapshotFile.parent.isDirectory) snapshotFile.parent.mkdirs()

    val sCfg = Server.Config()
    sCfg.transport          = osc.TCP
    sCfg.inputBusChannels   = 1
    sCfg.outputBusChannels  = 2
    sCfg.sampleRate         = sampleRate
    sCfg.blockSize          = smpIncr // ! so we can use decimation through ar -> kr
    sCfg.deviceName         = Some("ScalaCollider")

    println("Booting server...")
    watchFIFO()

    Server.run(sCfg)(serverStarted(_, config))
  }

  private[this] val smpIncr         = 32
  private[this] val sampleRate      = 44100

  private def serverStarted(s: Server, config: Config): Unit = {
    import config._
    println("Server started.")

    val bufKernel = Buffer(s)
    import Ops._
    val kernel    = mkFilterKernel()
    bufKernel.alloc(numFrames = kernel.length, numChannels = 1, completion = bufKernel.setnMsg(kernel))
    val bufLoop   = Buffer(s)
    bufLoop.alloc(numFrames = loopLen, numChannels = 1)
    bufLoop.zero()

    var oldFrames = 0
    if (snapshotFile.exists()) {
      try {
        val spec      = AudioFile.readSpec(snapshotFile)
        val numFrames = math.min(loopLen, spec.numFrames).toInt
        if (spec.numChannels == 1 && numFrames > 0) {
          bufLoop.read(path = snapshotFile.path, fileStartFrame = 0, numFrames = numFrames, bufStartFrame = 0)
          oldFrames = numFrames
        }
      } catch {
        case NonFatal(ex) =>
          println("While reading snapshot spec:")
          ex.printStackTrace()
          snapshotFile.delete()
      }
    }

    println(s"bufLoop: oldFrames = $oldFrames, numFrames = $loopLen")

    if (!snapshotFile.exists()) {
      try {
        val af = AudioFile.openWrite(snapshotFile,
          AudioFileSpec(AudioFileType.IRCAM, SampleFormat.Float, numChannels = 1, sampleRate = 44100))
        af.close()
      } catch {
        case NonFatal(ex) =>
          println("While creating empty snapshot:")
          ex.printStackTrace()
      }
    }

    val raf: RandomAccessFile = try {
      new RandomAccessFile(snapshotFile, "rws")
    } catch {
      case NonFatal(ex) =>
        println("While opening snapshot for writing:")
        ex.printStackTrace()
        null
    }

    val mainSynth = play {
      import ugen._
//      val in        = WhiteNoise.ar(0.25)
//      val in        = SinOsc.ar(100) * 0.25
      val in        = PhysicalIn.ar(0)
      val flt       = Convolution2.ar(in = in, kernel = bufKernel.id, frameSize = kernel.length)
      val fltK      = A2K.kr(flt)
      val recOff    = oldFrames
      RecordBuf.kr(in = fltK, buf = bufLoop.id, offset = recOff, recLevel = 1, preLevel = 0, run = 1, loop = 1, trig = 1)
      val bufValid  = Sweep.ar(trig = 0, speed = sampleRate.toDouble / smpIncr).min(loopLen)
      // val playTrig  = Impulse.ar(1.0/10) // XXX TODO
      val playTrig  = Impulse.ar(sampleRate / bufValid.max(1))
      val sig       = PlayBuf.ar(numChannels = 1, buf = bufLoop.id, speed = 1.0, trig = playTrig, loop = 1)
      val saveTrig  = Impulse.ar(1.0) - Impulse.ar(0)
      val saveCount = PulseCount.ar(saveTrig)
      SendTrig.ar(trig = saveTrig, value = saveCount)

      Out.ar(0, Pan2.ar(sig))
    }
    val nodeID = mainSynth.id

//    s.dumpOSC()

    def getAndSave(start: Int, stop: Int): Unit = {
      val range = start until stop
      println(s"getAndSave($start, $stop)")
      val fut = bufLoop.getn(range)
      import ExecutionContext.Implicits.global
      fut.foreach { vec =>
        // println(vec.mkString("Vec(", ", ", ")"))
        val fileOffset  = start * 4 + 1024
        val sz          = vec.size
        val arr         = new Array[Byte](sz * 4)
        var i = 0
        var j = 0
        while (i < sz) {
          val k = java.lang.Float.floatToIntBits(vec(i))
//          arr(j) = ((k >>> 24) & 0xFF).toByte; j += 1
//          arr(j) = ((k >>> 16) & 0xFF).toByte; j += 1
//          arr(j) = ((k >>>  8) & 0xFF).toByte; j += 1
//          arr(j) = ( k         & 0xFF).toByte; j += 1

          // little endian:
          arr(j) = ( k         & 0xFF).toByte; j += 1
          arr(j) = ((k >>>  8) & 0xFF).toByte; j += 1
          arr(j) = ((k >>> 16) & 0xFF).toByte; j += 1
          arr(j) = ((k >>> 24) & 0xFF).toByte; j += 1
          i += 1
        }
        raf.synchronized {
          if (raf.getFilePointer != fileOffset) {
            raf.seek(fileOffset)
          }
          raf.write(arr)
        }
      }
    }

    var lastWritten: Long = oldFrames

    if (raf != null) message.Responder.add(s) {
      case message.Trigger(`nodeID`, 0, saveCountF) =>
        val saveCount       = saveCountF.toInt
        val framesRecorded  = saveCount * 44100L / smpIncr
        val stopFrame       = oldFrames + framesRecorded
        val start0          = (lastWritten % loopLen).toInt
        val end0            = (stopFrame   % loopLen).toInt
        if (end0 > start0) {
          getAndSave(start0, end0)
        } else if (end0 < start0) {
          getAndSave(start0, loopLen)
          getAndSave(     0, end0   )
        }
        lastWritten = stopFrame

        println(s"Save count: $saveCount")
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