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
  val io = IO(new Bundle {
    val pixelLast = Input(Bool())
    val pixel = Input(Valid(UInt(8.W))) // image
    val diffSquared = Output(Vec(nbWorkers, Valid(SInt(18.W))))
  })

  assert(wSize%nbWorkers == 0)
  // Point where all the difference tables overlap
  // By symetry, we only need half of the search window
  // Buffer till first pixel with full search window
  val bufferSize = (wSize/2)*width + (wSize/2) 
  val endBuffer = bufferSize - 1
  val rowSize = width - wSize - 1
  val maxBatchJumps = (wSize / nbWorkers)
  assert(maxBatchJumps != 0)

  val rowBuffer = Module(new FIFO()(new BRAMConfig(
    1, 9, bufferSize, "", false, true, false)))

  val s_Drop :: s_Buffer :: s_StreamValid :: s_StreamDrop :: Nil = Enum(4)
  val state = RegInit(s_Buffer)

  val currPixel = RegInit(0.U(log2Ceil(bufferSize).W))
  val currStartOffst = RegInit(0.U(log2Ceil(bufferSize).W))
  val currRowPixel = RegInit(0.U(log2Ceil(rowSize).W))
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
    val lastRow = row === (wSize/2).U
    (row, lastRow)
  }

  val jumpNextRow = currStartOffst + (width - wSize + nbWorkers).U
  val jumpNextBatch = currStartOffst + nbWorkers.U

  val flush = WireInit(false.B)
  val flushOnceStreamed = ShiftRegister(flush, rowBuffer.outDelay)
  rowBuffer.io.flush := flush
  rowBuffer.io.enq.valid := io.pixel.valid && (state =/= s_Drop)
  rowBuffer.io.enq.bits  := Cat(io.pixelLast.asUInt, io.pixel.bits)
  val basePixel = rowBuffer.io.deq.bits(7,0)
  val lastPixel = rowBuffer.io.deq.bits(8).asBool
  val currPixelNext = currPixel + 1.U
  rowBuffer.io.deq.ready := (state === s_StreamValid || state === s_StreamDrop) && io.pixel.valid

  val dropFirst = true
  when(io.pixel.valid) {
    currPixel := currPixelNext
  }
  switch(state) {
    is(s_Drop) {
      when(io.pixel.valid) {
        when(currPixelNext === currStartOffst) {
          state := s_Buffer
        }
      }
    }
    is(s_Buffer) {
      when(io.pixel.valid) {
        when(currPixel === endBuffer.U) {
          state := s_StreamValid
          currRowPixel := 0.U
        }
      }
    }

    is(s_StreamValid) {
      when(io.pixel.valid) {
        currRowPixel := currRowPixel + 1.U
        when(io.pixelLast) {
          state := s_Drop
          when(isLastRowBatch) {
            when(isLastRow) {
              state := s_Buffer
            }
          }
          }.elsewhen(currRowPixel === rowSize.U) {
            state := s_StreamDrop
            currRowPixel := 0.U
          }
      }
    }
    // Drop stream beacause edge of picture and search window overlap
    is(s_StreamDrop) {
      when(io.pixel.valid) {
        currRowPixel := currRowPixel + 1.U
        when(io.pixelLast) {
          state := s_Drop
          when(isLastRowBatch) {
            when(isLastRow) {
              state := s_Buffer
            }
          }
        }.elsewhen(currRowPixel === (wSize-1).U) {
          state := s_StreamValid
          currRowPixel := 0.U
        }
      }
    }
  }

  when(io.pixelLast) {
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

  val validDelayed = ShiftRegister(rowBuffer.io.deq.ready 
                      && state === s_StreamValid, rowBuffer.outDelay)
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
    io.diffSquared(chan).valid := ShiftRegister(pixelWorkers(chan).valid, diffSquarer(chan).delay)
    // io.diffSquared(chan).valid := ShiftRegister(pixelDelayed, diffSquarer(chan).delay)
    // io.diffSquared(chan).valid := pixelWorkers(chan).valid // Buffer first
  }
}
