// See README.md for license details.

package filters

import chisel3._
import chisel3.util._

import modules._

class ComputeSumSquareOpt extends MultiIOModule {
  val io = IO(new Bundle {
    val currSquare = Input(SInt(18.W))
    val leftSquares = Input(SInt(18.W))
    val leftSums = Input(SInt(32.W))
    val topSquareSum = Input(SInt(32.W))
    val sum = Output(SInt(32.W))
  })

  io.sum := io.currSquare + io.topSquareSum + io.leftSums + io.leftSquares
}


class StreamSquaredSumOpt(
  val width : Int = 720, 
  val height : Int = 1280,
  val kSize : Int = 8
  ) extends MultiIOModule {

  val io = IO(new Bundle {
    val diffSquared = Input(Valid(SInt(18.W)))
    val sum = Output(Valid(SInt(32.W)))
  })
  // Pre-read is done, so shift input by bramDelay
  // Also makes it easier for synthetizer
  val diffSquared = ShiftRegister(io.diffSquared, 2)

  val compute = Module(new ComputeSumSquareOpt)
  // Falling edge of row
  val isRowDone = RegNext(diffSquared.valid) && !diffSquared.valid

  val sumBuffer = Module(new FIFOReady()(new BRAMConfig(
    4, 8, width, "", false, true, false)))
  val squareBuffer = Module(new FIFOReady()(new BRAMConfig(
    2, 9, kSize*width, "", false, true, false)))

  val (currRow, isImgDone) = Counter(isRowDone, height)
  val currCol = RegInit(0.U(log2Ceil(kSize).W))
  val isKernelRowDone = RegInit(false.B)
  val isKernelColDone = RegInit(false.B)
  val isFirstRowDone = RegInit(false.B)
  val isFirstColDone = RegInit(false.B)
  val isSumOutValid = RegInit(false.B)

  squareBuffer.io.enq.valid := io.diffSquared.valid
  squareBuffer.io.enq.bits := io.diffSquared.bits.asUInt
  sumBuffer.io.enq.valid := diffSquared.valid
  sumBuffer.io.enq.bits := compute.io.sum.asUInt

  // Pre-reading
  squareBuffer.io.deq.ready := isKernelRowDone && io.diffSquared.valid
  sumBuffer.io.deq.ready := isFirstRowDone && io.diffSquared.valid

  val flush = ShiftRegister(isImgDone, 2)
  squareBuffer.io.flush := flush
  sumBuffer.io.flush := flush

  when(diffSquared.valid) {
    currCol := currCol + 1.U
    isFirstColDone := true.B
    when(currCol === (kSize-1).U) {
      isKernelColDone := true.B
    }
  }
  when(isRowDone) {
    currCol := 0.U
    isKernelColDone := false.B
    isFirstRowDone := true.B
    isFirstColDone := false.B
  }
  when(currRow === kSize.U) {
    isKernelRowDone := true.B
  }.elsewhen(currRow === (kSize-1).U) {
    isSumOutValid := true.B
  }
  when(isImgDone) {
    isKernelRowDone := false.B
    isFirstRowDone := false.B
    isSumOutValid := false.B
  }

  // Get T+1: Pre-read values and compute for next cycle
  val (preTopSum, preTopSquare) = (
    WireInit(sumBuffer.io.deq.bits.asSInt),
    WireInit(squareBuffer.io.deq.bits.asSInt))
  val preSum = Mux(isFirstRowDone, preTopSum, 0.S)
  val preSquare = Mux(isKernelRowDone, preTopSquare, 0.S)
  val topPrecomp = RegNext(preSum - preSquare)
  // T
  val (topSum, topSquare) = (RegNext(preSum), RegNext(preSquare))
  val currSquare = diffSquared.bits
  // T-1 && T-kSize
  val leftSums = RegNext(compute.io.sum - topSum)
  val leftSquares = ShiftRegister(topSquare - currSquare, kSize)

  compute.io.currSquare := currSquare
  compute.io.leftSquares := Mux(isKernelColDone, leftSquares, 0.S)
  compute.io.leftSums := Mux(isFirstColDone, leftSums, 0.S)
  compute.io.topSquareSum := topPrecomp
   
  io.sum.valid := Mux(isKernelColDone, RegNext(diffSquared.valid) && isSumOutValid, false.B)
  io.sum.bits := RegNext(compute.io.sum)
}
