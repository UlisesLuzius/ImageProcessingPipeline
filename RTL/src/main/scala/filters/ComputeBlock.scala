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

class StreamSumSquare extends MultiIOModule {
  val io = IO(new Bundle {
    val squares = Input(new SquaresIO)
    val sums = Input(new Bundle {
      val top = SInt(32.W)  // RegNext(io.topleft)
      val left = SInt(32.W) // RegNext(io.sum)
      val topleft = SInt(32.W)
    })
    val sum = Output(SInt(32.W))
  })

  val squares = io.squares
  val sums = io.sums
  val squaresSum = WireInit(squares.bot - squares.top - squares.botleft + squares.topleft)
  val sumsSum = WireInit(sums.top + sums.left - sums.topleft)

  io.sum := squaresSum + sumsSum
}

class StreamSquares(
  val kSize : Int = 8, 
  val width : Int = 720, 
  val height : Int = 1280
) extends MultiIOModule {

  val io = IO(new Bundle {
    val runRow = Input(Bool())
    val diffSquared = Input(SInt(18.W))
    val squares = Output(new SquaresIO)
  })

  // Need to buffer kSize rows
  private val rowNumber = {
    val size = kSize*width/1024
    if(size == 0) {
      1
    } else {
      size
    }
  }
  val sizeBRAM = java.lang.Math.ceil(rowNumber) * 1024
  val rowBuffer = Module(new BRAM()(new BRAMConfig(
    2, 9, sizeBRAM.toInt, "", false, true, false)))
  val doneKernelRow = RegInit(false.B)
  val doneKernelCol = RegInit(false.B)
  val (currWritePixel, bufferDone) = Counter(io.runRow, width*kSize)
  val (currReadPixel, readBufferDone) = Counter( // Trick for BRAM delay
    (io.runRow && doneKernelRow) || bufferDone, width*kSize)
  val (currCol, rowDone) = Counter(io.runRow, width)
  val (currRow, imgDone) = Counter(rowDone, height)
  val colKernelDone = currCol === (kSize-1).U
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
  rowBuffer.portA.WE   := io.runRow
  rowBuffer.portA.ADDR := currWritePixel
  rowBuffer.portA.DI   := io.diffSquared.asUInt

  rowBuffer.portB.EN := io.runRow
  rowBuffer.portB.WE := 0.U
  rowBuffer.portB.ADDR := currReadPixel
  rowBuffer.portB.DI := DontCare
  val bufferPixel = rowBuffer.portB.DO.asSInt

  val bot = RegNext(io.diffSquared)
  val top = Mux(doneKernelRow, bufferPixel, 0.S)
  val botshift = ShiftRegister(bot, kSize-1)
  val topshift = ShiftRegister(top, kSize-1)
  val botleft = Mux(doneKernelCol, botshift, 0.S)
  val topleft = Mux(doneKernelCol, topshift, 0.S)

  io.squares.bot := bot
  io.squares.top := top
  io.squares.botleft := botleft
  io.squares.topleft := topleft
}
