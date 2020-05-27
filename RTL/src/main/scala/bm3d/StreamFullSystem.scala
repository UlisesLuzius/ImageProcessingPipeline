// See README.md for license details.

package filters

import chisel3._
import chisel3.util._

import modules._

class StreamFullSystem(
  val width : Int = 720, 
  val height : Int = 1280,
  val kSize : Int = 8, 
  val wSize : Int = 32,
  val nbWorkers : Int = 8
) extends MultiIOModule {

  val io = IO(new Bundle {
    val pixel = Input(Valid(UInt(8.W)))
    val sum = Output(Vec(nbWorkers, Valid(SInt(32.W))))
  })

  val streamDiffSquare = Module(new StreamDiffSquare(wSize, width, nbWorkers))
  val streamSquaredSum = Seq.fill(nbWorkers)(
    Module(new StreamSquaredSum(width-wSize, height-(wSize/2), kSize)))
  val pixelCount = RegInit(0.U(log2Ceil(width*height).W))
  val pixelLast = WireInit(false.B)
  when(io.pixel.valid) {
    pixelCount := pixelCount + 1.U
    when(pixelCount === (width*height-1).U) {
      pixelLast := true.B
      pixelCount := 0.U
    }
  }

  streamDiffSquare.io.pixelLast := pixelLast
  streamDiffSquare.io.pixel := io.pixel
  for(chan <- 0 until nbWorkers) {
    streamSquaredSum(chan).io.diffSquared := streamDiffSquare.io.diffSquared(chan)
    io.sum(chan) := streamSquaredSum(chan).io.sum
  }
}
