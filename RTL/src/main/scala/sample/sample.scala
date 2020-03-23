// See README.md for license details.

package sample

import chisel3._
import chisel3.util._

class Sample extends Module {
  val io = IO(new Bundle {
    val o = Output(Bool())
  })

  val s_1 :: s_2 :: Nil = Enum(2)
  val state = RegInit(s_1)
  val wire = WireInit(Bool(), DontCare)
  switch(state) {
    is(s_1) {
      wire := false.B
      state := s_2
    }
    is(s_2) {
      wire := true.B
      state := s_1
    }
  }

  io.o := wire
}
