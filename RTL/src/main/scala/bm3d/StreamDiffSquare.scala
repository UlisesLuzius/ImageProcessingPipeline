// See README.md for license details.

package filters

import chisel3._
import chisel3.util._

import modules._

class StreamDiffSquare(
  val wSize: Int = 4, // Size of search window 
  val width: Int = 720,
  val nbWorkers: Int = 2 // Nb of parallel workers
) extends MultiIOModule {
  assert(wSize%nbWorkers == 0)
  val bufferSize = wSize*width+wSize
  val maxBatchJumps = (wSize / nbWorkers)
  assert(maxBatchJumps != 0)

  val io = IO(new Bundle {
    val pixelLast = Input(Bool())
    val pixel = Input(Valid(UInt(8.W))) // image
    val diffSquared = Output(Vec(nbWorkers, Valid(SInt(18.W))))
  })

  val rowBuffer = Module(new FIFO()(new BRAMConfig(
    1, 9, bufferSize, "", false, true, false)))

  val s_Buffer :: s_Stream :: Nil = Enum(2)
  val state = RegInit(s_Buffer)

  val currPixel = RegInit(0.U(log2Ceil(bufferSize).W))
  val currStartOffst = RegInit(nbWorkers.U(log2Ceil(bufferSize).W))
  val (nbRowBatchJumps, isLastRowBatch) = {
    if(maxBatchJumps == 1) {
      val batch = RegInit(0.U)
      val lastBatch = true.B
      (batch, lastBatch)
    } else {
      val batch = RegInit(0.U(log2Ceil(maxBatchJumps).W))
      val lastBatch = batch === (maxBatchJumps-1).U
      (batch, lastBatch)
    }
  }
  val (nbRowJumps, isLastRow) = {
    val row = RegInit(0.U(log2Ceil(wSize).W))
    val lastRow = row === (wSize-1).U
    (row, lastRow)
  }

  val jumpNextRow = currStartOffst + (width - wSize + nbWorkers).U
  val jumpNextBatch = currStartOffst + nbWorkers.U

  val flush = WireInit(false.B)
  val flushOnceStreamed = ShiftRegister(flush, rowBuffer.outDelay)
  rowBuffer.io.flush := flush
  rowBuffer.io.enq.valid := io.pixel.valid
  rowBuffer.io.enq.bits  := Cat(io.pixelLast.asUInt, io.pixel.bits)
  val basePixel = rowBuffer.io.deq.bits(7,0)
  val lastPixel = rowBuffer.io.deq.bits(8).asBool
  rowBuffer.io.deq.ready := state === s_Stream

  switch(state) {
    is(s_Buffer) {
      when(io.pixel.valid) {
        currPixel := currPixel + 1.U
        when(currPixel === currStartOffst) {
          state := s_Stream
        }
      }
    }

    is(s_Stream) {
      when(io.pixelLast) {
        state := s_Buffer
        flush := true.B
        currPixel := 0.U
        currStartOffst := jumpNextBatch
        nbRowBatchJumps := nbRowBatchJumps + 1.U
        when(isLastRowBatch) {
          nbRowBatchJumps := 0.U
          nbRowJumps := nbRowJumps + 1.U
          currStartOffst := jumpNextRow
          when(isLastRow) {
            nbRowJumps := 0.U
            currStartOffst := 0.U
          }
        }
      }
    }
  }

  val validDelayed = ShiftRegister(rowBuffer.io.deq.ready, rowBuffer.outDelay)
  val pixelDelayed = ShiftRegister(io.pixel.bits, rowBuffer.outDelay)
  val pixelWorkers = Reg(Vec(nbWorkers, Valid(UInt(8.W))))
  pixelWorkers(0).bits := pixelDelayed
  pixelWorkers(0).valid := validDelayed
  for (i <- 1 until nbWorkers) { 
	pixelWorkers(i) := pixelWorkers(i-1) 
  }

  val diffSquarer = Seq.fill(nbWorkers)(Module(new SquareSub(16, 40, false)))
  for(chan <- 0 until nbWorkers) {
    diffSquarer(chan).io.ele := basePixel.zext.asSInt
    diffSquarer(chan).io.sub := pixelWorkers(chan).bits.zext.asSInt
    diffSquarer(chan).io.ce := true.B
    io.diffSquared(chan).bits := diffSquarer(chan).io.sumSquares
    io.diffSquared(chan).valid := pixelWorkers(chan).valid || validDelayed
  }
}
