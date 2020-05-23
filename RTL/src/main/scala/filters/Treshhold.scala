// See README.md for license details.

package filters

import chisel3._
import chisel3.util._

import modules._

// We assume patch size wont be bigger than 256*256
// tHard = maximal distance threshold between patches
// kHard = patch size
// norm2(patchRef - patchOther)^2 < tHard*(kHard^2) = tresh
class MatchPatch extends MultiIOModule {
  val cmdStop :: cmdStart :: Nil = Enum(2)
  val io = IO(new Bundle {
    val pixel = Decoupled(new Bundle {
      val base = UInt(8.W)
      val curr = UInt(8.W)
    })

    // Res
    val matched = Decoupled(new Bundle {
      val isMatched = Bool()
      val pDist = SInt(16.W)
    })

    // Settings 
    val thresh = Input(SInt(16.W)) // thresh - 1
    val size = Input(UInt(8.W))  // kHard*kHard - 1
    val cmd = Decoupled(cmdStart.cloneType)
  })

  val thresh = RegNext(io.thresh)


  val s_Idle :: s_Matching :: s_Done :: s_WaitConsume :: Nil = Enum(4)
  val state = RegInit(s_Idle)
  val isMatched = RegInit(false.B)
  val pDist = RegInit(0.S(16.W))

  val currPixel = RegInit(0.U(8.W))
  val squareAcc = Module(new SquareSub(16, 40, true))

  val blockDone = WireInit(currPixel === io.size)
  val totalSum = WireInit(squareAcc.io.sumSquares)

  squareAcc.io.ele := io.pixel.bits.base.asSInt
  squareAcc.io.sub := io.pixel.bits.curr.asSInt
  squareAcc.io.ce := io.pixel.fire
  squareAcc.io.resetSum.get := false.B

  switch(state) {
    is(s_Idle) {
      when(io.cmd.fire) {
        when(io.cmd.bits === cmdStart) {
          state := s_Matching
          currPixel := 0.U
        }
      }
    }
    is(s_Matching) {
      when(io.pixel.fire) {
        currPixel := currPixel + 1.U
        when(blockDone) { // Patch Done
          state := s_Done
        }
        // Optimization possible
        // when(totalSum > thresh) {
        //   squareAcc.io.resetSum.get := true.B
        //   state := s_Unmatched
        // }
      }
    }
    is(s_Done) {
      pDist := totalSum
      isMatched := (totalSum <= thresh)
      state := s_WaitConsume
    }
    is(s_WaitConsume) {
      when(io.matched.fire) {
        squareAcc.io.resetSum.get := true.B
        state := s_Idle
      }
    }
  }

  io.cmd.ready := state =/= s_Idle
  io.pixel.ready := state === s_Matching
  io.matched.valid := state === s_WaitConsume
  io.matched.bits.isMatched := isMatched
  io.matched.bits.pDist := isMatched
}
