// See README.md for license details.

package filters

import chisel3._
import chisel3.util._

import modules._

class SquaresIO extends Bundle {
  val bot = Input(SInt(18.W))
  val top = Input(SInt(18.W))
  val botleft = Input(SInt(18.W))
  val topleft = Input(SInt(18.W))
}

class SumsIO extends Bundle {
  val top = SInt(32.W)  // RegNext(io.topleft)
  val botleft = SInt(32.W) // RegNext(io.sum)
  val topleft = SInt(32.W)
}

class ComputeSumSquare extends MultiIOModule {

  val io = IO(new Bundle {
    val squares = Input(new SquaresIO)
    val sums = Input(new SumsIO)
    val sum = Output(SInt(32.W))
  })

  val squares = io.squares
  val sums = io.sums
  val squaresSum = WireInit(squares.bot - squares.top - squares.botleft + squares.topleft)
  val sumsSum = WireInit(sums.top + sums.botleft - sums.topleft)

  io.sum := squaresSum + sumsSum
}

class StreamSquares(
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

class StreamSums(
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
  val firstRowDone = rowDone
  val firstColDone = RegNext(io.runRow)
  val doneFirstRow = RegInit(false.B)
  val doneFirstCol = RegInit(false.B)
  val (currWritePixel, bufferCycleWr) = Counter(io.sum.valid, width)
  val (currReadPixel, bufferCycleRd) = Counter(
    (io.runRow && doneFirstRow && !imgDone) || (io.sum.valid && rowDone), width)

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

  // Note, to lower compute timing, we can pre-sum here
  val top = Mux(doneFirstRow, bufferSum, 0.S)
  val botshift = RegNext(io.sum.bits)
  val topshift = RegNext(top)
  val botleft = Mux(doneFirstCol, botshift, 0.S)
  val topleft = Mux(doneFirstCol, topshift, 0.S)

  io.sums.top := top
  io.sums.botleft := botleft
  io.sums.topleft := topleft
}


class StreamSquaredSum(
  val kSize : Int = 8, 
  val width : Int = 720, 
  val height : Int = 1280
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

  io.sum.valid := RegNext(io.diffSquared.valid)
  io.sum.bits := RegNext(sum)

}
