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

object StreamFullSystemGen extends App {
  val annotations = Seq.empty
  val arguments = Array(
    "--emit-modules", "verilog",
    "--target-dir", "verilog")

  val (width, height, kSize, wSize, nbWorkers) =
      (720,     1280,     8,    32, 8)

  (new ChiselStage).execute(Array("-X", "verilog") ++ arguments, 
    ChiselGeneratorAnnotation(() => new StreamFullSystem(
      width, height, kSize, wSize, nbWorkers)) +: annotations)
}


class StreamFullSystemDriver(duv: StreamFullSystem) {
  def unsinged(x: Int): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)
  def unsinged(x: Byte): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)

  private def init: Unit = {
    duv.io.pixel.valid.poke(false.B)
    duv.io.pixel.bits.poke(0.U)
  }

  this.init

  def runDUV(): Unit = {
    val pixelArray: Array[Int] = (for (
      i <- 0 until duv.height;
      j <- 0 until duv.width
    ) yield (j)).toArray // + i*duv.width)).toArray

    duv.clock.step(3)
    for(jump <- 0 until (duv.wSize/duv.nbWorkers)*(duv.wSize/2)) {
      duv.io.pixel.valid.poke(true.B)
      for (
        row <- 0 until duv.height;
        col <- 0 until duv.width
      ) {
        val currPixel = row * duv.width + col
        val pixel = pixelArray(currPixel)
        duv.io.pixel.bits.poke(pixel.U)
        duv.clock.step()
      }
      duv.io.pixel.valid.poke(false.B)
      duv.clock.step(10)
    }
    duv.clock.step(20)
  }
}

class StreamFullSystemTester extends FlatSpec with ChiselScalatestTester {

  val annos = Seq(
    VerilatorBackendAnnotation
    ,TargetDirAnnotation("test/fullSystem")
    ,WriteVcdAnnotation)
    //)

  behavior of "duv FullSystem"

  //*
  it should "return sum of difference from an image input" in {
    test(new StreamFullSystem(10, 6, 4, 4, 1)).withAnnotations(annos) { dut =>
      val duvDrv = new StreamFullSystemDriver(dut)
      duvDrv.runDUV()
    }
  }
  // */
}

