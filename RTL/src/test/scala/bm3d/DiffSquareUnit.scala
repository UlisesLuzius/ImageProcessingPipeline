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

class StreamDiffSquareDriver(duv: StreamDiffSquare) {

  private def init: Unit = {
    duv.io.pixelLast.poke(false.B)
    duv.io.pixel.bits.poke(0.U)
    duv.io.pixel.valid.poke(false.B)
  }

  this.init

  def runDUV(rows: Int, useAssert : Boolean): Unit = {
    val pixelArray: Array[Int] = (for (
      i <- 0 until rows;
      j <- 0 until duv.width
    ) yield (j)).toArray // + i*duv.width)).toArray

    for(jump <- 0 until duv.maxBatchJumps*(duv.wSize/2)) {
      duv.io.pixel.valid.poke(true.B)
      for (
        row <- 0 until rows;
        col <- 0 until duv.width
      ) {
        val currPixel = row * duv.width + col
        val currSquare = pixelArray(currPixel)
        duv.io.pixel.bits.poke(currSquare.U)
        if(row == rows-1 && col == duv.width-1) {
          duv.io.pixelLast.poke(true.B)
        }
        duv.clock.step()
      }
      duv.io.pixel.valid.poke(false.B)
      duv.io.pixelLast.poke(false.B)
      duv.clock.step(3)
    }
  }
}

class StreamDiffSquareTester extends FlatSpec with ChiselScalatestTester {

  val annos = Seq(
    VerilatorBackendAnnotation
    ,TargetDirAnnotation("test/diffSquares")
    ,WriteVcdAnnotation)
    //)

  behavior of "duv DiffSquare"

  //*
  it should "return Difference Squared of Pixels" in {
    test(new StreamDiffSquare(8, 30, 4)).withAnnotations(annos) { dut =>
      val duvDrv = new StreamDiffSquareDriver(dut)
      duvDrv.runDUV(10, true)

      // duvDrv.runDUV(10, true)

    }
  }
  // */
}

