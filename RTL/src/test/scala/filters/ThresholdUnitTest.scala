// See README.md for license details.

package duvs

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.language.reflectiveCalls
import scala.language.implicitConversions


import org.scalatest._

import chisel3._
import chisel3.experimental._

import chiseltest._
import chiseltest.internal._
import chiseltest.experimental.TestOptionBuilder._

import firrtl.options.TargetDirAnnotation
import firrtl.{EmitCircuitAnnotation}

import imageTrans._
import filters._

import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}
import firrtl.{EmitAllModulesAnnotation}
import firrtl.options.{TargetDirAnnotation}

object ThresholdGen extends App {
  val annotations = Seq.empty
  val arguments = Array(
    "--emit-modules", "verilog",
    "--target-dir", "verilog")

  (new ChiselStage).execute(Array("-X", "verilog") ++ arguments, 
    ChiselGeneratorAnnotation(() => new MatchPatch) +: annotations)
}


class MatchPatchDriver(duv: MatchPatch) {
  implicit def unsinged(x: Int): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)
  implicit def unsinged(x: Byte): BigInt = (BigInt(x >>> 1) << 1) | BigInt(x & 1)

  private def init: Unit = {
    duv.io.pixel.valid.poke(false.B)
    duv.io.pixel.bits.base.poke(0.U)
    duv.io.pixel.bits.curr.poke(0.U)

    duv.io.matched.ready.poke(false.B)

    duv.io.thresh.poke(0.S)
    duv.io.size.poke(0.U)
    duv.io.cmd.valid.poke(false.B)
    duv.io.cmd.bits.poke(0.U)
  }

  this.init

  private def ready: Boolean = duv.io.cmd.ready.peek.litToBoolean
  private def busy: Boolean = !duv.io.cmd.ready.peek.litToBoolean
  private def pokeCmd(cmd: UInt): Unit = {
    if (ready) {
      duv.io.cmd.bits.poke(cmd)
      duv.io.cmd.valid.poke(true.B)
      duv.clock.step()
      duv.io.cmd.valid.poke(false.B)
    } else {
      println("Device is not ready to receive commands")
    }
  }

  private def pokePixels(pixBase: BigInt,  pixCurr: BigInt) {
    assert(duv.io.pixel.ready.peek.litToBoolean)
    duv.io.pixel.bits.base.poke(pixBase.U)
    duv.io.pixel.bits.curr.poke(pixCurr.U)
  }

  private def pokePatch(base: Array[Byte], curr: Array[Byte]) {
    for(pix <- base) {
      pokePixels(base(pix), base(pix))
    }
  }

  def runDUV(picArray: Array[Byte], kHard: Int, tHard: Int, 
    imgDriver: ImageTransformDriver): Unit = {

    val thresh = kHard*kHard*tHard
    val picWidth = imgDriver.width
    val picHight = imgDriver.hight

    duv.io.thresh.poke((tHard-1).S)
    duv.io.size.poke((kHard*kHard).U)

    pokeCmd(duv.cmdStart)
    while(!ready) { duv.clock.step() }

    var pixelCnt = 0
    var pixelBase = 0

    while(pixelCnt < kHard*kHard) {
      duv.clock.step()
      pixelCnt+=1
    }
  }

}

class GroupTester extends FlatSpec with ChiselScalatestTester {

  val annos = Seq(
    VerilatorBackendAnnotation
    ,TargetDirAnnotation("test/group")
    ,WriteVcdAnnotation)
    //)

  behavior of "duv RGB of a picture"

  val width = 451
  val hight = 300

  it should "return a duved RGB channel of a picture" in {
    test(new MatchPatch).withAnnotations(annos) { dut =>
      val duvDrv = new MatchPatchDriver(dut)
      val imgDrv = new ImageTransformDriver(300, 451)
      val picArray = imgDrv.openFile("/frame_cat_noisy_5")
      val img = duvDrv.runDUV(picArray, 8, 2500, imgDrv)
    }
  }
}

