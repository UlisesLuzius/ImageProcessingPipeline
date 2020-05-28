// See README.md for license details.

package filters

import chisel3._
import chisel3.util._

import modules._

class StreamFullSystem(
  val width : Int = 720, 
  val height : Int = 1280,
  val kSize : Int = 8, 
  val wSize : Int = 32,
  val nbWorkers : Int = 8
) extends MultiIOModule {

  val io = IO(new Bundle {
    val pixel = Input(Valid(UInt(8.W)))
    val sum = Output(Vec(nbWorkers, Valid(SInt(32.W))))
  })

  val streamDiffSquare = Module(new StreamDiffSquare(wSize, width, nbWorkers))
  val streamSquaredSum = Seq.fill(nbWorkers)(
    Module(new StreamSquaredSum(width-wSize, height-(wSize/2), kSize)))
  val pixelCount = RegInit(0.U(log2Ceil(width*height).W))
  val pixelLast = WireInit(false.B)
  when(io.pixel.valid) {
    pixelCount := pixelCount + 1.U
    when(pixelCount === (width*height-1).U) {
      pixelLast := true.B
      pixelCount := 0.U
    }
  }

  streamDiffSquare.io.pixelLast := pixelLast
  streamDiffSquare.io.pixel := io.pixel
  for(chan <- 0 until nbWorkers) {
    streamSquaredSum(chan).io.diffSquared := streamDiffSquare.io.diffSquared(chan)
    io.sum(chan) := streamSquaredSum(chan).io.sum
  }
}

// This module just wraps input and output with AXI mapped register
// This helps to synthetize without optimizing paths
class AXIStreamFullSystem(
  val width : Int = 720, 
  val height : Int = 1280,
  val kSize : Int = 8, 
  val wSize : Int = 32,
  val nbWorkers : Int = 8
) extends MultiIOModule {


  val readOnlyBits: Seq[Boolean] = Seq.fill(nbWorkers)(true)
  val pulseOnlyBits: Seq[Boolean] = Seq.fill(nbWorkers)(false)
  val readOnly = readOnlyBits ++ Seq(true,false)
  val pulseOnly = pulseOnlyBits ++ Seq(false,false)
  val cfgAxiMM = new AxiMemoryMappedRegFileConfig(nbWorkers + 2, 
    readOnly, pulseOnly)
  val regFile  = Module(new AxiMemoryMappedRegFile()(cfgAxiMM))
  val regValues = WireInit(VecInit(Seq.fill(cfgAxiMM.nbrReg)
    (0.U(AxiLiteConsts.dataWidth.W))))
  def regOut(reg:Int)(msb:Int, lsb:Int) = regFile.io.regsOutput(reg)(msb,lsb)
  def regIn(reg:Int) = regValues(reg)
  regFile.io.regsInput := regValues

  val streamer = Module(new StreamFullSystem(width, height, kSize, wSize, nbWorkers))

  streamer.io.pixel.bits := regOut(nbWorkers + 1)(7,0)
  streamer.io.pixel.valid := regOut(nbWorkers + 1)(8,8)
  for(chan <- 0 until nbWorkers) {
    regIn(chan) := streamer.io.sum(chan).bits.asUInt
  }

  val validConcat = streamer.io.sum.foldLeft(0.U) { 
    (acc, bits) => Cat(acc, bits.valid.asUInt) }
  regIn(nbWorkers) := validConcat


  val io = IO(new Bundle {
    val axiLite = AxiLiteSlave(cfgAxiMM.axiLiteConfig)
  })
  io.axiLite <> regFile.io.axiLite
}

