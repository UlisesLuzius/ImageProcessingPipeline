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

import imageTrans._

object StreamFullSystemGen extends App {
  val annotations = Seq.empty
  val arguments = Array(
    "--emit-modules", "verilog",
    "--target-dir", "verilog/fullSystem")

  val (width, height, kSize, wSize, nbWorkers) =
      (  720,   1280,     8,    32,        16)

  (new ChiselStage).execute(Array("-X", "verilog") ++ arguments, 
    ChiselGeneratorAnnotation(() => new StreamFullSystem(
      width, height, kSize, wSize, nbWorkers)) +: annotations)
}


// AXI wrapper to check resources utilization
object AXIStreamFullSystemGen extends App {
  val annotations = Seq.empty
  val arguments = Array(
    "--emit-modules", "verilog",
    "--target-dir", "verilog/AxiFullSystem")

  val (width, height, kSize, wSize, nbWorkers) =
      (  720,   1280,     8,    32,         4)

  (new ChiselStage).execute(Array("-X", "verilog") ++ arguments, 
    ChiselGeneratorAnnotation(() => new AXIStreamFullSystem(
      width, height, kSize, wSize, nbWorkers)) +: annotations)
}




class StreamFullSystemDriver(duv: StreamFullSystem) {
  def unsigned(x: Int): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)
  def unsigned(x: Byte): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)

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

  def runDUVimg(pixelArray: Array[Byte]): ArrayBuffer[ArrayBuffer[Int]] = {

    val res: ArrayBuffer[ArrayBuffer[Int]] = ArrayBuffer.fill(duv.wSize*duv.wSize/2)(
      ArrayBuffer.fill(duv.height*duv.width)(0))
    var streamedOut: Long = 0
    var currWorkers: Long = 0
    var currOffst: Int = 0
    duv.clock.step(3)
    fork {
      for(jump <- 0 until (duv.wSize/duv.nbWorkers)*(duv.wSize/2)) {
        duv.io.pixel.valid.poke(true.B)
        for (
          row <- 0 until duv.height;
          col <- 0 until duv.width
        ) {
          val currPixel = row * duv.width + col
          val pixel = pixelArray(currPixel)
          duv.io.pixel.bits.poke(unsigned(pixel).U)
          duv.clock.step()
        }
        duv.io.pixel.valid.poke(false.B)
        duv.clock.step(5)
        currOffst = currOffst + duv.nbWorkers
      }
    }.fork {
      var currPixel = (duv.wSize/2)*duv.width + (duv.wSize/2)
      var cnt = 0
      var lastOffst = 0
      while(cnt < duv.width*duv.height*duv.wSize*duv.wSize/2) {
        if(duv.io.sum(0).valid.peek.litToBoolean) {
          for(i <- 0 until duv.nbWorkers) {
            res(currOffst + i)(currPixel) = duv.io.sum(i).bits.peek.litValue.toInt 
          }
          currPixel = currPixel + 1
        }
        if(lastOffst != currOffst) {
          lastOffst = currOffst
          currPixel = (duv.wSize/2)*duv.width + (duv.wSize/2)
        }
        duv.clock.step()
        
        cnt = cnt + 1
        if(cnt % 1000 == 0) {
          println("1000 clock cycles passed")
        }
      }
    }.join()
    return res
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
  it should "return sum of difference from a generated image" in {
    test(new StreamFullSystem(20, 15, 4, 8, 4)).withAnnotations(annos) { dut =>
      val duvDrv = new StreamFullSystemDriver(dut)
      duvDrv.runDUV()
    }
  }
  // */
}

class GenSumTable extends FlatSpec with ChiselScalatestTester {

  val annos = Seq(
    VerilatorBackendAnnotation
    ,TargetDirAnnotation("test/fullSystem")
    //,WriteVcdAnnotation)
    )

  behavior of "duv FullSystem"

  //*
  it should "return sum of difference from an image input" in {
    test(new StreamFullSystem(451, 300, 8, 32, 8)).withAnnotations(annos) { dut =>
      val imgDrv = new ImageTransformDriver(300, 451)
      val img = imgDrv.openFile("/singleChannel")
      val duvDrv = new StreamFullSystemDriver(dut)
      val sumTable: Seq[Seq[Int]] = duvDrv.runDUVimg(img)
    }
  }
  // */
}

