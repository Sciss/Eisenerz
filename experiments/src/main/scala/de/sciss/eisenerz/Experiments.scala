/*
 *  Experiments.scala
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

import com.jhlabs.image.SmartBlurFilter
import de.sciss.file._
import de.sciss.kollflitz.Ops._
import de.sciss.numbers.Implicits._
import de.sciss.synth.io.{AudioFile, AudioFileSpec}

import scala.collection.breakOut

object Experiments {
  val baseDir = userHome / "Documents" / "projects" / "Eisenerz" / "image_work2"

  var composite: Array[Array[Double]] = _
  var width : Int = _
  var height: Int = _

  def main(args: Array[String]): Unit = {
    require(baseDir.isDirectory)
//    average()
//    difference()
    median()
  }

  // compares strings insensitive to case but sensitive to integer numbers
  def compareName(s1: String, s2: String): Int = {
    // this is a quite ugly direct translation from a Java snippet I wrote,
    // could use some scala'fication

    val n1  = s1.length
    val n2  = s2.length
    val min = math.min(n1, n2)

    var i = 0
    while (i < min) {
      var c1 = s1.charAt(i)
      var c2 = s2.charAt(i)
      var d1 = Character.isDigit(c1)
      var d2 = Character.isDigit(c2)

      if (d1 && d2) {
        // Enter numerical comparison
        var c3 = c1
        var c4 = c2
        var sameChars = c3 == c4
        do {
          i += 1
          val c5 = if (i < n1) s1.charAt(i) else 'x'
          val c6 = if (i < n2) s2.charAt(i) else 'x'
          d1 = Character.isDigit(c5)
          d2 = Character.isDigit(c6)
          if (sameChars && c5 != c6) {
            c3 = c5
            c4 = c6
            sameChars = false
          }
        }
        while (d1 && d2)

        if (d1 != d2) return if (d1) 1 else -1  // length wins
        if (!sameChars) return c3 - c4          // first diverging digit wins
        i -= 1
      }
      else if (c1 != c2) {
        c1 = Character.toUpperCase(c1)
        c2 = Character.toUpperCase(c2)

        if (c1 != c2) {
          c1 = Character.toLowerCase(c1)
          c2 = Character.toLowerCase(c2)

          if (c1 != c2) {
            // No overflow because of numeric promotion
            return c1 - c2
          }
        }
      }

      i += 1
    }
    n1 - n2
  }

  def median(): Unit = {
    val sideLen   = 3 // 2
    val medianLen = sideLen * 2 + 1
    val thresh    = 0.3333
//    val thresh    = 0.2 / 150

    val inputs    = baseDir.children(f => f.base.startsWith("frame-") && f.ext == "jpg")
    val output    = baseDir / "out_median.png"
    val sorted    = inputs.sortWith((a, b) => compareName(a.name, b.name) < 0) .take(/* 42 */ 240)
    val numInput  = sorted.size
    require(numInput >= medianLen, s"Need at least $medianLen images")
    println(s"$numInput images.")

    val fltBlur = new SmartBlurFilter
    fltBlur.setRadius(7)
    fltBlur.setThreshold(20)
    // val fltHisto  = new Histogram

    def blur(in: BufferedImage): BufferedImage = fltBlur.filter(in, null)

    val images      = new Array[BufferedImage]       (medianLen)
    val luminances  = new Array[Array[Array[Double]]](medianLen)

    def readOne(in: File, idx: Int): Unit = {
      println(s"Reading ${in.name}...")
      val bufIn   = ImageIO.read(in)
      val blurImg = blur(bufIn)
      val lum     = extractBrightness(blurImg)
//      val (mean, variance) = (lum.flatMap(_.toIndexedSeq)(breakOut): IndexedSeq[Double]).meanVariance
//      val stdDev = math.sqrt(variance)
////      println(f"mean $mean%1.2f, stddev $stdDev%1.2f")
//      var y = 0
//      while (y < height) {
//        val ch = lum(y)
//        var x = 0
//        while (x < width) {
//          ch(x) = (ch(x) - mean) / stdDev
//          x += 1
//        }
//        y += 1
//      }

      images    (idx) = bufIn
      luminances(idx) = lum
    }

    for (idx <- 0 until (medianLen - 1)) {
      readOne(sorted(idx), idx = idx)
    }

    width     = images(0).getWidth
    height    = images(0).getHeight
    composite = Array.ofDim(3, width * height)
    
    val mask  = Array.ofDim[Double](height, width)
    val maskB = Array.ofDim[Double](height, width)

    for (idx <- (medianLen - 1) until numInput) {
      readOne(sorted(idx), idx = idx % medianLen)
      val cIdx = (idx - sideLen) % medianLen
      val lumC = luminances(cIdx)
      
      // calculate mask
      {
        var y = 0
        while (y < height) {
          var x = 0
          while (x < width) {
            val comp = lumC(y)(x)
            var min  = comp
            var max  = comp
            var i = 0
            while (i < medianLen) {
              val d = luminances(i)(y)(x)
              if (d < min) min = d
              if (d > max) max = d
              i += 1
            }
            mask(y)(x) = if (max - min > thresh && (comp == min || comp == max)) 1.0 else 0.0
            x += 1
          }
          y += 1
        }
      }
      // blur mask
      for (_ <- 0 until 3 /* 2 */) {
        var y = 0
        while (y < height) {
          var x = 0
          while (x < width) {
            var sum = 0.0
            var cnt = 0
            var yi = math.max(0, y - 1)
            val ys = math.min(height, y + 2)
            while (yi < ys) {
              var xi = math.max(0, x - 1)
              val xs = math.min(width, x + 2)
              while (xi < xs) {
                sum += mask(yi)(xi)
                cnt += 1
                xi += 1
              }
              yi += 1
            }
            maskB(y)(x) = sum / cnt
            x += 1
          }
          y += 1
        }

        // copy back for recursion
        y = 0
        while (y < height) {
          System.arraycopy(maskB(y), 0, mask(y), 0, width)
          y += 1
        }
      } // recursion

      // mix to composite
      (0 until 3).foreach { ch =>
        val chan  = extractChannel(images(cIdx), ch)
        val compC = composite(ch)
        var y = 0
        while (y < height) {
          var x = 0
          while (x < width) {
            compC(y * width + x) += maskB(y)(x) * chan(y)(x)
            x += 1
          }
          y += 1
        }
      }
    }

    writeNormalizedOut(output, perChannel = true)
    val outputBin = output.replaceExt("bin")
    println(s"Saving binary file to $outputBin...")
    val af = AudioFile.openWrite(outputBin, AudioFileSpec(numChannels = 3, sampleRate = 44100))
    af.write(composite.map(_.map(_.toFloat)))
    af.close()
  }

  def writeNormalizedOut(output: File, perChannel: Boolean = false): Unit = {
    println(s"Writing ${output.name}...")
    val planeMins = composite.map(_.min) // .min
    val planeMaxs = composite.map(_.max) // .max
    // println(f"Channel values: min = $planeMin%1.2f, max = $planeMax%1.2f")
    val bufOut    = {
      val res = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
      val gTmp = res.createGraphics()
      gTmp.setColor(Color.black)
      gTmp.fillRect(0, 0, width, height)
      gTmp.dispose()
      res
    }
    if (perChannel) {
      composite.zipWithIndex.foreach { case (plane, ch) =>
        val add0 = -planeMins(ch)
        val mul  = 1.0 / (planeMaxs(ch) - planeMins(ch))
        fillChannel(plane.grouped(width).toArray, bufOut, chan = ch, add = add0, mul = mul)
      }
    } else {
      val planeMin = planeMins.min
      val planeMax = planeMaxs.max
      val add0 = -planeMin
      val mul  = 1.0 / (planeMax - planeMin)
      composite.zipWithIndex.foreach { case (plane, ch) =>
        fillChannel(plane.grouped(width).toArray, bufOut, chan = ch, add = add0, mul = mul)
      }
    }
    ImageIO.write(bufOut, "png", output)
  }

  def difference(): Unit = {
    val inputs = baseDir.children(f => f.base.startsWith("frame-") && f.ext == "jpg")
    require(inputs.nonEmpty)
    println(s"${inputs.size} images.")
    val output  = baseDir / "out_diff.png"
    val sorted  = inputs.sortWith((a, b) => compareName(a.name, b.name) < 0) .take(20)
    // sorted.foreach(println)

    val fltBlur = new SmartBlurFilter
    fltBlur.setRadius(7)
    fltBlur.setThreshold(20)
    // val fltHisto  = new Histogram

    def blur(in: BufferedImage): BufferedImage = fltBlur.filter(in, null)

    var bufInA: BufferedImage = null
    var bufInB: BufferedImage = null
    var blurA : BufferedImage = null
    var blurB : BufferedImage = null
    val meanVarA = new Array[(Double, Double)](3)
    val meanVarB = new Array[(Double, Double)](3)

    def readOne(inB: File): Unit = {
      println(s"Reading ${inB.name}...")
      bufInB  = ImageIO.read(inB)
      blurB   = blur(bufInB)
    }

    locally {
      val inB = sorted.head
      readOne(inB)
      width   = bufInB.getWidth
      height  = bufInB.getHeight
      (0 until 3).foreach { ch =>
        val chanB     = extractChannel(blurB , ch).flatten
        meanVarB(ch)  = (chanB: IndexedSeq[Double]).meanVariance
        // val meanB     = meanVarB(ch)._1
        // val varianceB = meanVarB(ch)._2
        // println(f"[$ch] mean $meanB%1.2f, variance $varianceB%1.2f")
      }
    }

    composite = Array.ofDim(3, width * height)

    sorted/* .take(5) */.foreachPair { case (inA, inB) =>
      bufInA      = bufInB // ImageIO.read(inA)
      blurA       = blurB // blur(bufInA)
      readOne(inB)
      (0 until 3).foreach { ch =>
        val origB = extractChannel(bufInB, ch).flatten
        val chanA = extractChannel(blurA , ch).flatten
        val chanB = extractChannel(blurB , ch).flatten
        meanVarA(ch)  = meanVarB(ch)
        val meanA     = meanVarA(ch)._1
        val varianceA = meanVarA(ch)._2
        meanVarB(ch) = (chanB: IndexedSeq[Double]).meanVariance
        val meanB     = meanVarB(ch)._1
        val varianceB = meanVarB(ch)._2
        // println(f"[$ch] mean $meanB%1.2f, variance $varianceB%1.2f")

        var i = 0
        val planeOut = composite(ch)
        val mul  = varianceA / varianceB
        // val add0 = meanA - meanB
        // println(f"offset $add0%1.2f, gain $mul%1.2f")
        (origB, chanA, chanB).zipped.foreach { (orig, a, b) =>
          val c     = (b - meanB) * mul + meanA
          val gain0 = a absdif c /* b */
          val gain  = gain0.atan
          planeOut(i) += orig * gain
          i += 1
        }
      }
      bufInA.flush()
      blurA .flush()
    }

    writeNormalizedOut(output)
  }

  def average(): Unit = {
    val inputs  = baseDir.children(f => f.base.startsWith("lapse_short_") && f.ext == "jpg")
    require(inputs.nonEmpty)
    println(s"${inputs.size} images.")
    val output = baseDir / "out_lapse_short.png"
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

    writeNormalizedOut(output)
  }

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

  // cf. https://stackoverflow.com/questions/596216
  def extractBrightness(in: BufferedImage): Array[Array[Double]] = {
    Array.tabulate(in.getHeight) { y =>
      Array.tabulate(in.getWidth) { x =>
        val rgb = in.getRGB(x, y)
        val r   = ((rgb & 0xFF0000) >> 16) / 255f
        val g   = ((rgb & 0x00FF00) >>  8) / 255f
        val b   = ( rgb & 0x0000FF       ) / 255f
        val lum = (0.299 * r.squared + 0.587 * g.squared + 0.114 * b.squared).sqrt
        lum
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
