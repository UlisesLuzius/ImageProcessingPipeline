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
  val io = IO(new Bundle {
    val pic = Decoupled(new Bundle {
      val reference = SInt(8.W)
      val current = SInt(8.W)
    })

    // Res
    val matched = Decoupled(Bool())

    // Settings 
    val thresh = Input(SInt(16.W)) // thresh - 1
    val size = Input(UInt(8.W))  // kHard*kHard - 1
    val start = Input(Bool())
    val busy = Output(Bool())
  })

  val thresh = RegNext(io.thresh)

  val s_Idle :: s_Matching :: s_Done :: s_WaitConsume :: Nil = Enum(4)
  val state = RegInit(s_Idle)
  val isMatched = RegInit(false.B)

  val currPixel = RegInit(0.U(8.W))
  val squareAcc = Module(new SquareSub(16, 40))

  val blockDone = WireInit(currPixel === io.size)
  val totalSum = WireInit(squareAcc.io.sumSquares)

  squareAcc.io.ele := io.pic.bits.reference.asSInt
  squareAcc.io.sub := io.pic.bits.current.asSInt
  squareAcc.io.ce := io.pic.fire
  squareAcc.io.resetSum := false.B

  switch(state) {
    is(s_Idle) {
      when(io.start) {
        state := s_Matching
        currPixel := 0.U
      }
    }
    is(s_Matching) {
      when(io.pic.fire) {
        currPixel := currPixel + 1.U
        when(blockDone) { // Patch Done
          state := s_Done
        }
        // Optimization possible
        // when(totalSum > thresh) {
        //   squareAcc.io.resetSum := true.B
        //   state := s_Unmatched
        // }
      }
    }
    is(s_Done) {
      isMatched := (totalSum <= thresh)
      state := s_WaitConsume
    }
    is(s_WaitConsume) {
      when(io.matched.fire) {
        squareAcc.io.resetSum := true.B
        state := s_Idle
      }
    }
  }

  io.busy := state =/= s_Idle
  io.pic.ready := state === s_Matching
  io.matched.valid := state === s_WaitConsume
  io.matched.bits := isMatched
}
