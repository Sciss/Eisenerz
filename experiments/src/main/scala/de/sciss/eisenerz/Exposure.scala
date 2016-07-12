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

import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import de.sciss.file._
import de.sciss.numbers.Implicits._

import scala.collection.breakOut

object Exposure extends App {
  val baseDir = userHome / "Documents" / "projects" / "Eisenerz" / "image_work"
  require(baseDir.isDirectory)
  val inputs  = baseDir.children(f => f.base.startsWith("lapse_short_") && f.ext == "jpg")
  val output  = baseDir / "out_lapse_short.png"

  var composite: Array[Array[Double]] = _
  var width : Int = _
  var height: Int = _
  require(inputs.nonEmpty)
  println(s"${inputs.size} images.")

  inputs.sortBy(_.name) /* .take(3) */.zipWithIndex.foreach { case (in, idx) =>
    println(s"Reading ${in.name}...")
    val bufIn = ImageIO.read(in)
    val planes = (0 until 3).map(extractChannel(bufIn, _).flatten)(breakOut): Array[Array[Double]]
    if (idx == 0) {
      width     = bufIn.getWidth
      height    = bufIn.getHeight
      println(s"width = $width, height = $height")
      composite = planes
    } else {
      require(width  == bufIn.getWidth)
      require(height == bufIn.getHeight)
      (planes zip composite).foreach { case (a, b) =>
        add(a, 0, b, 0, a.length)
      }
    }
    bufIn.flush()
  }
  println(s"Writing ${output.name}...")
  val planeMin  = composite.map(_.min).min
  val planeMax  = composite.map(_.max).max
  println(f"Channel values: min = $planeMin%1.2f, max = $planeMax%1.2f")
  val bufOut    = {
    val res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val gTmp = res.createGraphics()
    gTmp.setColor(Color.black)
    gTmp.fillRect(0, 0, width, height)
    gTmp.dispose()
    res
  }
  val add       = -planeMin
  val mul       = 1.0 / (planeMax - planeMin)
  composite.zipWithIndex.foreach { case (plane, ch) =>
    fillChannel(plane.grouped(width).toArray, bufOut, chan = ch, add = add, mul = mul)
  }
  ImageIO.write(bufOut, "png", output)
  sys.exit()

  /** Adds input to output. */
  def add(in: Array[Double], inOff: Int, out: Array[Double], outOff: Int, len: Int): Unit = {
    var i     = inOff
    var j     = outOff
    val stop  = i + len
    while (i < stop) {
      out(j) += in(i)
      i += 1
      j += 1
    }
  }

  def extractChannel(in: BufferedImage, chan: Int): Array[Array[Double]] = {
    val shift = chan * 8
    Array.tabulate(in.getHeight) { y =>
      Array.tabulate(in.getWidth) { x =>
        val i = (in.getRGB(x, y) >>> shift) & 0xFF
        i.toDouble / 0xFF
      }
    }
  }

  def fillChannel(in: Array[Array[Double]], out: BufferedImage, chan: Int, add: Double = 0.0, mul: Double = 1.0): Unit = {
    val shift = chan * 8
    val mask  = ~(0xFF << shift)
    for (y <- in.indices) {
      val v = in(y)
      for (x <- v.indices) {
        val d = (v(x) + add) * mul
        val i = (d.clip(0, 1) * 0xFF + 0.5).toInt << shift
        val j = out.getRGB(x, y)
        val k = j & mask | i
        out.setRGB(x, y, k)
      }
    }
  }

  def mulC(a: Array[Array[Double]], b: Array[Array[Double]], scale: Double = 1.0): Unit = {
    for (y <- a.indices) {
      val va = a(y)
      val vb = b(y)
      for (x <- va.indices by 2) {
        val re = va(x) * vb(x)   - va(x+1) * vb(x+1)
        val im = va(x) * vb(x+1) + va(x+1) * vb(x)
        va(x)   = re * scale
        va(x+1) = im * scale
      }
    }
  }
}
