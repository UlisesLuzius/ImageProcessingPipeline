// See README.md for license details.

package sample

import java.io.File

import org.scalatest._

import chisel3._
import chisel3.experimental._

import chiseltest._
import chiseltest.internal._
import chiseltest.experimental.TestOptionBuilder._

import firrtl.options.TargetDirAnnotation

class SampleDriver(c: Sample) {
  for(i <- 0 until 100 ) {
    c.clock.step()
  }
}

class SampleTester extends FlatSpec with ChiselScalatestTester {

  val annos = Seq(
    VerilatorBackendAnnotation,
    TargetDirAnnotation("./test"),
    WriteVcdAnnotation)

  behavior of "Sample execution small modules"

  it should "Executesomething small" in {

    test(new Sample)
      .withAnnotations(annos) { dut =>
        val drv = new SampleDriver(dut)
      }
  }
}
