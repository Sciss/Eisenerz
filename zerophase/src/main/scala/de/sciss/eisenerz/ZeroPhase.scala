/*
 *  ZeroPhase.scala
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

import java.io.PrintStream

import de.sciss.file._
import de.sciss.synth.Server
import de.sciss.synth.io.AudioFile
import de.sciss.{numbers, osc, synth}
import scopt.OptionParser

object ZeroPhase {
  final case class Config(soundFile       : File    = file("/media/pi/exposure/sound.w64"),
                          debug           : Boolean = false,
                          runDurMinutes   : Int     = 0,
                          ignoreXRUNs     : Boolean = false,
                          sampleRate      : Int     = 44100,
                          balance         : Double  = -0.17,
                          diskBuf         : Int     = 32768
                         )

  def main(args: Array[String]): Unit = {
    val parser = new OptionParser[Config]("Accelerate") {
      opt[File]('f', "soundfile")    text "Sound file" action { (x, c) => c.copy(soundFile = x) }
      opt[Int ]('d', "shutdown")     text "Run duration before shutting down in minutes (zero for no timeout)" action { (x, c) => c.copy(runDurMinutes = x) }
      opt[Unit]('y', "debug")        text "Enable debug logging" action { (_, c) => c.copy(debug = true) }
      opt[Unit]('x', "ignore-xruns") text "Disable reboot upon seeing XRUNs" action { (_, c) => c.copy(ignoreXRUNs = true) }
      opt[Int ]('b', "buffer")       text "Disk buffer size (must be power of two)" action {
        (x, c) => c.copy(diskBuf = x)} validate { x =>
        import numbers.Implicits._
        if (x.isPowerOfTwo) Right(()) else Left("Must be power of two")
      }
    }
    parser.parse(args, Config()).fold(sys.exit(1))(run)
  }

  def restartAfterFailure(): Unit = {
    import scala.sys.process._
    Seq("sudo", "reboot", "now").!
  }

  def shutdown(): Unit = {
    import scala.sys.process._
    Seq("sudo", "shutdown", "now").!
  }

  def watchFIFO(config: Config): Unit = {
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
        super.print(x)
        if (x.contains("command FIFO full")) {
          super.print("--- DETECTED!")
          if (!config.ignoreXRUNs) restartAfterFailure()
        }
      }
    }
    Console.setOut(printStream) // yes, deprecated my a**
  }

  private[this] var SHUTDOWN = Long.MaxValue

  def run(config: Config): Unit = {
    import scala.sys.process._
    Seq("killall", "scsynth").!

    if (config.runDurMinutes > 0) {
      SHUTDOWN = System.currentTimeMillis() + (config.runDurMinutes * 60 * 1000L)
    }

    // import config._
    // if (!snapshotFile.parent.isDirectory) snapshotFile.parent.mkdirs()

    val sCfg = Server.Config()
    sCfg.transport          = osc.TCP
    sCfg.inputBusChannels   = 0
    sCfg.outputBusChannels  = 2
    sCfg.sampleRate         = config.sampleRate
    sCfg.deviceName         = Some("ScalaCollider")

    println("Booting server...")
    watchFIFO(config)

    Server.run(sCfg)(serverStarted(_, config))
  }

  private def serverStarted(s: Server, config: Config): Unit = {
    val spec = AudioFile.readSpec(config.soundFile)
    require(spec.numChannels == 2)

    import synth._
    import Ops._

    val dfName = "sound"
    val df = SynthDef(dfName) {
      import ugen._
      val buf   = "buf".ir
      val dur   = "dur".ir(1.0)
      val pos   = "bal".kr(0.0)
      val disk  = DiskIn.ar(numChannels = 2, buf = buf, loop = 1)
      val env   = Env.sine
      val eg    = EnvGen.ar(env, timeScale = dur, doneAction = freeSelf)
      val sig   = disk * eg
      val bal   = Balance2.ar(inL = ChannelProxy(sig, 0), inR = ChannelProxy(sig, 1), pos = pos)
      Out.ar(0, bal)
    }
    df.recv(s, completion = { _: SynthDef => spawnOne() })

    def rrand(lo: Double, hi: Double): Double =
      math.random * (hi - lo) + lo

    def spawnOne(): Unit = {
      if (System.currentTimeMillis() > SHUTDOWN) shutdown()
      else {
        val syn         = Synth(s)
        val dur         = rrand(60.0, 240.0)
        val durF        = (dur * spec.sampleRate).toLong
        val startFrame  = rrand(0, spec.numFrames - durF).toInt

        if (config.debug) println(f"Spawn startFrame = $startFrame%d, dur = $dur%1.1f")

        val playSynth = { buf: Buffer =>
          val m = syn.newMsg(dfName, args = Seq[ControlSet]("buf" -> buf.id, "dur" -> dur, "bal" -> config.balance))
          s ! m // we need `syn.play()` in future ScalaCollider
          syn.onEnd {
            buf.close(); buf.free()
            spawnOne()
          }
          ()
        }

        Buffer.cue(s, path = config.soundFile.path,
          startFrame = startFrame, numChannels = 2, bufFrames = config.diskBuf,
          completion = playSynth)
      }
    }
  }
}