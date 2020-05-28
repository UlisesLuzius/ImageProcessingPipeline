// See README.md for license details.

package filters

import chisel3._
import chisel3.util._

import modules._

class ComputeSumSquareOpt extends MultiIOModule {

  val io = IO(new Bundle {
    val squares = Input(new Bundle {
      val input = Input(SInt(18.W))
      val shifted = Input(SInt(18.W))
    })
    val sums = Input(new Bundle{
      val topSum = Input(SInt(32.W))
      val left = Input(SInt(32.W))
    })
    val sum = Output(SInt(32.W))
  })

  io.sum := io.sums.left + io.sums.topSum + io.squares.input + io.squares.shifted
}

class StreamSquaresOpt(
  val kSize : Int = 8, 
  val width : Int = 720, 
  val height : Int = 1280
) extends MultiIOModule {

  val io = IO(new Bundle {
    val runRow = Input(Bool())
    val runSquares = Output(Bool())
    val diffSquared = Input(SInt(18.W))
    val squares = Output(new SquaresIO)
  })

  // Need to buffer kSize rows
  val rowBuffer = Module(new BRAM()(new BRAMConfig(
    2, 9, kSize*width, "", false, true, false)))
  val runSquares = RegNext(io.runRow)
  val doneKernelRow = RegInit(false.B)
  val doneKernelCol = RegInit(false.B)
  val (currCol, rowDone) = Counter(io.runRow, width)
  val (currRow, imgDone) = Counter(rowDone, height)
  val (currRowKernel, startTop) = Counter(!doneKernelRow && rowDone, kSize)
  val (currWritePixel, bufferCycleWr) = Counter(io.runRow, width*kSize)
  val (currReadPixel, bufferCycleRd) = Counter(
    (io.runRow && doneKernelRow && !imgDone) || startTop, width*kSize)
  val colKernelDone = currCol === kSize.U
  val rowKernelDone = currRow === kSize.U

  when(RegNext(imgDone)) {
    doneKernelRow := false.B
  }.elsewhen(rowKernelDone) {
    doneKernelRow := true.B
  }

  when(RegNext(rowDone)) {
    doneKernelCol := false.B
  }.elsewhen(colKernelDone) {
    doneKernelCol := true.B
  }

  rowBuffer.portA.EN   := true.B
  rowBuffer.portA.WE   := Fill(2, io.runRow.asUInt)
  rowBuffer.portA.ADDR := currWritePixel
  rowBuffer.portA.DI   := io.diffSquared.asUInt

  rowBuffer.portB.EN := io.runRow
  rowBuffer.portB.WE := 0.U
  rowBuffer.portB.ADDR := currReadPixel
  rowBuffer.portB.DI := DontCare
  val bufferPixel = rowBuffer.portB.DO.asSInt

  // Note, to lower compute timing, we can pre-sum here
  // and use DSP registers for shifting
  val bot = RegNext(io.diffSquared)
  val top = Mux(doneKernelRow, bufferPixel, 0.S)
  val botshift = ShiftRegister(bot, kSize)
  val topshift = ShiftRegister(top, kSize)
  val botleft = Mux(doneKernelCol, botshift, 0.S)
  val topleft = Mux(doneKernelCol, topshift, 0.S)

  io.squares.bot := bot
  io.squares.top := top
  io.squares.botleft := botleft
  io.squares.topleft := topleft
  io.runSquares := runSquares
}

class StreamSumsOpt(
  val width : Int = 720, 
  val height : Int = 1280
) extends MultiIOModule {

  val io = IO(new Bundle {
    val runRow = Input(Bool())
    val sum = Input(Valid(SInt(32.W)))
    val sums = Output(new SumsIO)
  })

  // Need to buffer a single row
  val rowBuffer = Module(new BRAM()(new BRAMConfig(
    4, 8, width, "", false, true, false)))
  val (currCol, rowDone) = Counter(io.runRow, width)
  val (currRow, imgDone) = Counter(rowDone, height)
  val firstRowDone = RegNext(rowDone)
  val firstColDone = RegNext(io.runRow)
  val doneFirstRow = RegInit(false.B)
  val doneFirstCol = RegInit(false.B)
  val (currWritePixel, bufferCycleWr) = Counter(io.sum.valid, width)
  val (currReadPixel, bufferCycleRd) = Counter(
    ((io.runRow && doneFirstRow ) || (io.sum.valid && rowDone)) && !imgDone, width)

  when(RegNext(imgDone)) {
    doneFirstRow := false.B
  }.elsewhen(firstRowDone) {
    doneFirstRow := true.B
  }

  when(RegNext(rowDone)) {
    doneFirstCol := false.B
  }.elsewhen(firstColDone) {
    doneFirstCol := true.B
  }

  rowBuffer.portA.EN   := true.B
  rowBuffer.portA.WE   := Fill(4, io.sum.valid.asUInt)
  rowBuffer.portA.ADDR := currWritePixel
  rowBuffer.portA.DI   := io.sum.bits.asUInt

  rowBuffer.portB.EN := io.runRow
  rowBuffer.portB.WE := 0.U
  rowBuffer.portB.ADDR := currReadPixel
  rowBuffer.portB.DI := DontCare
  val bufferSum = rowBuffer.portB.DO.asSInt

  // Note, to lower compute timing, we could pre-sum here
  val top = Mux(doneFirstRow, bufferSum, 0.S)
  val botshift = RegNext(io.sum.bits)
  val topshift = RegNext(top)
  val botleft = Mux(doneFirstCol, botshift, 0.S)
  val topleft = Mux(doneFirstCol, topshift, 0.S)

  io.sums.top := top
  io.sums.botleft := botleft
  io.sums.topleft := topleft
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

  val getSquares = Module(new StreamSquares(kSize, width, height))
  val getSums = Module(new StreamSums(width, height))
  val compute = Module(new ComputeSumSquare)
  val sum = compute.io.sum

  getSquares.io.runRow := io.diffSquared.valid
  getSums.io.runRow := io.diffSquared.valid

  getSquares.io.diffSquared := io.diffSquared.bits
  compute.io.squares <> getSquares.io.squares
  compute.io.sums <> getSums.io.sums
  getSums.io.sum.bits := sum
  getSums.io.sum.valid := getSquares.io.runSquares

  val squaredValid = ShiftRegister(io.diffSquared.valid, 2)

  val kernelDone = RegInit(false.B)
  val colSum = RegInit(0.U(log2Ceil(width).W))
  when(squaredValid) {
    colSum := colSum + 1.U
    when(colSum === (width-kSize).U) {
      kernelDone := true.B
    }.elsewhen(colSum === (width-1).U) {
      kernelDone := false.B
      colSum := 0.U
    }
  }

  io.sum.valid := Mux(kernelDone, squaredValid, false.B)
  io.sum.bits := RegNext(sum)
}
