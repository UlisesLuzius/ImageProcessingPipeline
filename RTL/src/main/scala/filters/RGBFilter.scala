// See README.md for license details.

package filters

import chisel3._
import chisel3.util._

object ColorSpaces {
  val YUV   = 0
  val YCBCR = 1
  val OPP   = 2
  val RGB   = 3
}

class Filter(val hight: Int, val width: Int) extends MultiIOModule {
  val io = IO(new Bundle {
    val consume = Output(Bool())
    val pixel_i = Input(Valid(UInt(32.W)))
    val pixel_o = Output(Valid(UInt(32.W)))

    val req = Flipped(Decoupled(UInt(3.W)))
    val busy = Output(Bool())
  })

  val s_Idle :: s_Filtering :: Nil = Enum(2)

  val state = RegInit(s_Idle)
  val busy = RegInit(false.B)
  val pixelValid = RegNext(false.B)

  val pixelBits = RegInit(0.U(32.W))
  val selColors = RegInit(0.U(3.W))
  val pixConsume = WireInit(false.B)
  val (currPixel, picDone) = Counter(pixConsume, width*hight)
  val (currColor, pixDone) = Counter(pixConsume, 3)

  switch(state) {
    is(s_Idle) {
      when(io.req.fire) {
        selColors := io.req.bits
        state := s_Filtering
        busy := true.B
      }
    }
    is(s_Filtering) {
	  pixConsume := io.pixel_i.valid
	  when(picDone) {
		state := s_Idle
		busy := false.B
	  }
    }
  }

  pixelValid := io.pixel_i.valid
  pixelBits := Mux(selColors(currColor), io.pixel_i.bits, 0.U) 

  io.req.ready := !busy
  io.consume := busy
  io.busy := busy
  io.pixel_o.valid := pixelValid
  io.pixel_o.bits  := pixelBits
}
