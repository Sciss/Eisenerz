/*
 *  Rails.scala
 *  (Eisenerz)
 *
 *  Copyright (c) 2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenerz

import java.awt.BasicStroke
import java.awt.geom.Line2D
import java.io.File

import de.sciss.file._
import de.sciss.pdflitz.Generate
import de.sciss.pdflitz.Generate.QuickDraw
import de.sciss.synth.io.AudioFile
import scopt.OptionParser

import scala.swing.Dimension

object Rails {
  case class Config(soundFile : File = userHome / "Documents" / "projects" / "Eisenerz" / "audio_work" / "Muehle-LPercLim.w64",
                    output    : File = userHome / "Documents" / "projects" / "Eisenerz" / "rails" / "rails.pdf",
                    startFrame: Long = 44100L * 60,
                    stopFrame : Long = 44100L * 70,
                    numThreads: Int  = 60,
                    width     : Int  = 1000,
                    height    : Int  = 200,
                    threshold : Double = 0.125 / 2.5,
                    stroke    : Double = 2.0)

  def main(args: Array[String]): Unit = {
    val parser = new OptionParser[Config]("Accelerate") {
      opt[File  ]('f', "soundfile")    text "Sound file" action { (x, c) => c.copy(soundFile = x) }
      opt[Long  ]("start")             text "Start frame in sound file"  action { (x, c) => c.copy(startFrame = x) }
      opt[Long  ]("stop" )             text "Stop frame in sound file"   action { (x, c) => c.copy(stopFrame  = x) }
      opt[Int   ]('n', "num")          text "Number of threads or loops" action { (x, c) => c.copy(numThreads = x) }
      opt[Int   ]('w', "width")        text "Image width"                action { (x, c) => c.copy(width      = x) }
      opt[Int   ]('h', "height")       text "Image width"                action { (x, c) => c.copy(height     = x) }
      opt[Double]('t', "thresh")       text "Amplitude threshold"        action { (x, c) => c.copy(threshold  = x) }
      opt[Double]('s', "stroke")       text "Stroke width"               action { (x, c) => c.copy(stroke     = x) }
    }
    parser.parse(args, Config()).fold(sys.exit(1))(run)
  }

  def run(config: Config): Unit = {
    import config._
    val af    = AudioFile.openRead(soundFile)
    val inLen = (stopFrame - startFrame).toInt
    val buf   = af.buffer(inLen)
    af.seek(startFrame)
    af.read(buf)
    af.close()
    val buf0  = buf(0)

    val scaleBuf = inLen.toDouble / numThreads

    val threads = Array.tabulate(numThreads) { i =>
      val x0 = ( i      * scaleBuf).toInt
      val x1 = ((i + 1) * scaleBuf).toInt
      var min = Double.PositiveInfinity
      var max = Double.NegativeInfinity
      for (x <- x0 until x1) {
        val f = buf0(x)
        min = math.min(min, f)
        max = math.max(max, f)
      }
      println(f"min = $min%1.2f, max = $max%1.2f")
      val y0 = if (max >  threshold)  1.0 else 0.05
      val y1 = if (min < -threshold) -1.0 else -0.05
      (y1, y0)
    }

    val scaleX = width.toDouble / numThreads
    val scaleY = height.toDouble / 2
    val offY   = scaleY

    val view  = QuickDraw(new Dimension(width, height)) { g2 =>
      g2.setStroke(new BasicStroke(stroke.toFloat))
      val ln = new Line2D.Double
      threads.zipWithIndex.foreach { case ((y1, y0), i) =>
        val x  = i * scaleX
        val y2 = y1 * scaleY + offY
        val y3 = y0 * scaleY + offY
        println(s"line($x, $y2, $x, $y3")
        ln.setLine(x, y2, x, y3)
        g2.draw(ln)
      }
    }
    // SaveAction(qd :: Nil)
    Generate(output, view, usePreferredSize = false, margin = 0, overwrite = true)
  }
}
