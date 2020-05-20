// See README.md for license details.

package duvs

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.language.reflectiveCalls
import scala.language.implicitConversions

import org.scalatest._

import filters._

import chisel3._
import chisel3.experimental._

import chiseltest._
import chiseltest.internal._
import chiseltest.experimental.TestOptionBuilder._

import firrtl.options.TargetDirAnnotation
import firrtl.{EmitCircuitAnnotation}

import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import firrtl.{EmitAllModulesAnnotation}
import firrtl.options.{TargetDirAnnotation}

object StreamGen extends App {
  val annotations = Seq.empty
  val arguments = Array(
    "--emit-modules", "verilog",
    "--target-dir", "verilog")

  (new ChiselStage).execute(Array("-X", "verilog") ++ arguments, 
    ChiselGeneratorAnnotation(() => new StreamSquares) +: annotations)
}


class StreamSquaresDriver(duv: StreamSquares) {
  def unsinged(x: Int): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)
  def unsinged(x: Byte): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)

  private def init: Unit = {
    duv.io.runRow.poke(false.B)
    duv.io.diffSquared.poke(0.S)
    // duv.io.squares.top.poke(0.S)
    // duv.io.squares.topleft.poke(0.S)
    // duv.io.squares.bot.poke(0.S)
    // duv.io.squares.botleft.poke(0.S)
  }

  this.init

  private def assertOutput(
    pixelArray: Array[Int],
    currPixel : Int,
    hasTop: Boolean,
    hasLeft: Boolean
  ) = {
    val top     = duv.io.squares.top.peek.litValue.toInt
    val bot     = duv.io.squares.bot.peek.litValue.toInt
    val topLeft = duv.io.squares.topleft.peek.litValue.toInt
    val botLeft = duv.io.squares.botleft.peek.litValue.toInt
    val topRowOffst = duv.kSize*duv.width
    val leftColOffst = duv.kSize - 1
    assert(bot == pixelArray(currPixel))
    if(hasTop) {
      assert(top == pixelArray(currPixel - topRowOffst))

      if(hasLeft) {
        assert(topLeft == pixelArray(currPixel - topRowOffst - leftColOffst))
      }
    } else {
      assert(top == 0)
      assert(topLeft == 0)
    }
    if(hasLeft) {
      assert(botLeft == pixelArray(currPixel - leftColOffst))
    }
  }

  def runDUV(rowDelay: Int, useAssert : Boolean): Unit = {
    val pixelArray: Array[Int] = (for (
      i <- 0 until duv.height;
      j <- 0 until duv.width
    ) yield (j + i*duv.width)).toArray

    duv.io.runRow.poke(true.B)
    for (
      row <- 0 until duv.height;
      col <- 0 until duv.width
    ) {
      val hasTop  = duv.kSize <= row
      val hasLeft = duv.kSize <= col
      val currPixel = row * duv.width + col
      val currSquare = pixelArray(currPixel)
      duv.io.diffSquared.poke(currSquare.S)
      duv.clock.step()
      if(useAssert) assertOutput(pixelArray, currPixel, hasTop, hasLeft)
      if(col == (duv.width - 1)) {
        duv.io.runRow.poke(false.B)
        duv.clock.step(rowDelay)
        duv.io.runRow.poke(true.B)
      }
    }
    duv.io.runRow.poke(false.B)
    duv.clock.step(duv.kSize*2)
  }

}

class StreamTester extends FlatSpec with ChiselScalatestTester {

  val annos = Seq(
    VerilatorBackendAnnotation
    ,TargetDirAnnotation("test/computeBlock")
    ,WriteVcdAnnotation)
    //)

  behavior of "duv RGB of a picture"

  /*
  it should "return verify kSize dependencies when streaming squares" in {
    test(new StreamSquares(4, 15, 10)).withAnnotations(annos) { dut =>
      val duvDrv = new StreamSquaresDriver(dut)
      duvDrv.runDUV(3, true)
    }
  }
  // */

  //*
  it should "verify minimal rowDelay, and multiple images in a row" in {
    test(new StreamSquares(4, 15, 10)).withAnnotations(annos) { dut =>
      val duvDrv = new StreamSquaresDriver(dut)
      duvDrv.runDUV(2, true)
      duvDrv.runDUV(1, true)
      duvDrv.runDUV(4, true)
      duvDrv.runDUV(2, true)
    }
  }
  // */


  //*
  it should "verify real 720p" in {
    test(new StreamSquares(8, 40, 720)).withAnnotations(annos) { dut =>
      val duvDrv = new StreamSquaresDriver(dut)
      duvDrv.runDUV(2, true)
    }
  } 
  // */


}

