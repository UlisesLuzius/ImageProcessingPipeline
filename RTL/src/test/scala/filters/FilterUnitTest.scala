// See README.md for license details.

package filters

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.language.reflectiveCalls

import org.scalatest._

import chisel3._
import chisel3.experimental._

import chiseltest._
import chiseltest.internal._
import chiseltest.experimental.TestOptionBuilder._

import firrtl.options.TargetDirAnnotation
import firrtl.{EmitCircuitAnnotation}

import imageTrans._

import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import firrtl.{EmitAllModulesAnnotation}
import firrtl.options.{TargetDirAnnotation}

object FilterGen extends App {
  val annotations = Seq.empty
  val arguments = Array(
    "--emit-modules", "verilog",
    "--target-dir", "verilog")

  (new ChiselStage).execute(
    Array("-X", "verilog") ++ arguments, 
    ChiselGeneratorAnnotation(() => new Filter(451, 300)) +: annotations)
}


class FilterDriver(filter: Filter) {
  val imgDriver = new ImageTransformDriver(filter.hight, filter.width)

  private def init: Unit = {
    filter.io.pixel_i.bits.poke(0.U)
    filter.io.pixel_i.valid.poke(false.B)
    filter.io.req.valid.poke(false.B)
    filter.io.req.bits.poke(0.U)
  }
  this.init

  def selRGB(selR: Int, selG: Int, selB: Int): Int = 
    (selR << 2) | (selG << 1) | (selB << 0)

  private def ready: Boolean = filter.io.req.ready.peek.litToBoolean
  private def busy: Boolean = filter.io.busy.peek.litToBoolean
  private def consume: Boolean = filter.io.consume.peek.litToBoolean

  private def start(colorSel: Int): Unit = {
    filter.io.req.valid.poke(true.B)
    filter.io.req.bits.poke(colorSel.U)
    assert(filter.io.req.ready.peek.litToBoolean)
    filter.clock.step()
    filter.io.req.valid.poke(false.B)
  }

  private def startPixels: Unit = filter.io.pixel_i.valid.poke(true.B)
  private def stopPixels: Unit = filter.io.pixel_i.valid.poke(false.B)
  private def pokePixel(value: Int) = {
    filter.io.pixel_i.bits.poke(value.U)
    filter.clock.step()
  }

  private def isPixelValid: Boolean = filter.io.pixel_o.valid.peek.litToBoolean
  private def getPixelValue: Int = filter.io.pixel_o.bits.peek.litValue.toInt

  def runFilter(imgPath: String, resPath:String, colorSel: Int): Array[Byte]= {
    assert(!busy)
    start(colorSel)

    assert(busy)
    assert(consume)

    val picStream = imgDriver.getPicStream(imgPath)
    val resStream = imgDriver.getPicStream(resPath)
	var pixel_byte: Byte = 0.toByte
    var imgRes: Array[Byte] = Array(0.toByte)

    fork {
      startPixels
      var pixelI: Int = picStream.read()
      var steps = 0
      while(steps < filter.hight*filter.width + 2 && pixelI != -1) {
        steps += 1
        pokePixel(pixelI)
        pixelI = picStream.read()
      }
      println("Done:streaming image:" + steps)
      stopPixels
      filter.clock.step()
    }.fork {
      assert(busy)
      var steps = 0
      var stepsTot = 0
      var pixelRes: Int = resStream.read()
      //var imgRes =  ArrayBuffer[Int]()
      while(steps < filter.hight*filter.width + 2 && pixelRes != -1) {
        steps += 1
        if(isPixelValid) {
          stepsTot += 1
          assert(getPixelValue == pixelRes) // Step by step Verification
          pixelRes = resStream.read() // Get next pixel
        }
      }

      println("Done:Outstreaming picture:" + steps)
      filter.clock.step()

    }.join()
    assert(!busy)
    this.init
    picStream.close()
    resStream.close()
    // assert(imgDriver.matches(imgRes, resPath)) // Full system verification
    return Array(0.toByte)
  }
}

class FilterTester extends FlatSpec with ChiselScalatestTester {

  val annos = Seq(
    VerilatorBackendAnnotation,
    TargetDirAnnotation("test"),
    WriteVcdAnnotation)

  behavior of "Filter RGB of a picture"

  val width = 451
  val hight = 300

  it should "return a filtered RGB channel of a picture" in {
    test(new Filter(hight, width)).withAnnotations(annos) { dut =>
      val duvDrv = new FilterDriver(dut)
      val img = duvDrv.runFilter("/frame_cat_base", "/frame_cat_011", duvDrv.selRGB(0,1,1))
    }
  }
}

