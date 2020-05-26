package modules

import chisel3._
import chisel3.experimental._
import chisel3.util._

class SquareSub(val SIZEIN: Int  = 16, val SIZEOUT: Int = 40, val acc : Boolean) extends Module {
  val delay = 3 // Reg: a|b -> diff -> m -> adder
  val io = IO(new Bundle(){
    val ele = Input(SInt((SIZEIN-1).W))
    val sub = Input(SInt((SIZEIN-1).W))
    val ce = Input(Bool())
    val resetSum = if(acc) Some(Input(Bool())) else None
    val sumSquares = Output(SInt((SIZEOUT+1).W))
  })
  private val dsp = Module(new SquareSub_infer(SIZEIN, SIZEOUT, acc))
  dsp.io.clk <> this.clock.asUInt
  dsp.io.ce <> io.ce
  dsp.io.a <> io.ele
  dsp.io.b <> io.sub
  dsp.io.sload <> !(io.resetSum.getOrElse(true.B))
  dsp.io.accum_out <> io.sumSquares
}

class SquareSub_infer(val SIZEIN: Int = 16, val SIZEOUT: Int = 40, val acc : Boolean) 
extends BlackBox(Map(
  "SIZEIN" -> SIZEIN,
  "SIZEOUT" -> SIZEOUT
)) with HasBlackBoxInline {
  val io = IO(new Bundle{
    val clk = Input(Bool())
    val ce = Input(Bool())
    val a = Input(SInt((SIZEIN-1).W))
    val b = Input(SInt((SIZEIN-1).W))
    val sload = Input(Bool())
    val accum_out = Output(SInt((SIZEOUT+1).W))
  })


  if(acc) 
    setInline("SquareSub_infer.v",
    s"""
    |  // This module performs subtraction of two inputs, squaring on the diff
    |  // and then accumulation 
    |  // This can be implemented in 1 DSP Block (Ultrascale architecture)
    |  module SquareSub_infer #(
    |    parameter SIZEIN = 16,
    |    parameter SIZEOUT = 40
    |  ) (
    |     input clk, // clock input 
    |     input ce,  // clock enable
    |     input sload; // synchronous load
    |     input signed [SIZEIN-1:0]  a, // 1st input 
    |     input signed [SIZEIN-1:0]  b, // 2nd input 
    |    output signed [SIZEOUT+1:0] accum_out  // accumulator output
    |  );
    |
    |  // Declare registers for intermediate values
    |  reg signed [SIZEIN-1:0]   a_reg, b_reg;   
    |  reg signed [SIZEIN:0]     diff_reg;
    |  reg signed [2*SIZEIN+1:0] m_reg;
    |  reg signed [SIZEOUT-1:0]  adder_out, old_result;
    |  reg                       sload_reg;
    |
    |  always @(sload_reg or adder_out)
    |  if (sload_reg)
    |    old_result <= 0;
    |  else
    |    // 'sload' is now and opens the accumulation loop.
    |    // The accumulator takes the next multiplier output
    |    // in the same cycle.
    |    old_result <= adder_out;
    |
    |  always @(posedge clk)
    |  if (ce) begin
    |    a_reg <= a;
    |    b_reg <= b;
    |    diff_reg <= a_reg - b_reg;  
    |    m_reg <= diff_reg * diff_reg;
    |    sload_reg <= sload;
    |    // Store accumulation result into a register
    |    adder_out <= old_result + m_reg;
    |  end
    |
    |  // Output accumulation result
    |  assign accum_out = adder_out;
    |
    | endmodule
    """.stripMargin)
  else 
    setInline("SquareSub_infer.v",
    s"""
    |  // This module performs subtraction of two inputs, squaring on the diff
    |  // and then accumulation 
    |  // This can be implemented in 1 DSP Block (Ultrascale architecture)
    |  module SquareSub_infer #(
    |    parameter SIZEIN = 16,
    |    parameter SIZEOUT = 40
    |  ) (
    |     input clk, // clock input 
    |     input ce,  // clock enable
    |     input sload, // synchronous load
    |     input signed [SIZEIN-1:0]  a, // 1st input 
    |     input signed [SIZEIN-1:0]  b, // 2nd input 
    |    output signed [SIZEOUT+1:0] accum_out // adder output
    |  );
    |
    |  // Declare registers for intermediate values
    |  reg signed [SIZEIN-1:0]   a_reg, b_reg;   
    |  reg signed [SIZEIN:0]     diff_reg;
    |  reg signed [2*SIZEIN+1:0] m_reg;
    |  reg signed [SIZEOUT-1:0]  adder_out;
    |  reg                       sload_reg;
    |
    |  always @(posedge clk)
    |  if (ce) begin
    |    a_reg <= a;
    |    b_reg <= b;
    |    diff_reg <= a_reg - b_reg;  
    |    m_reg <= diff_reg * diff_reg;
    |    sload_reg <= sload;
    |    // Store adder result into a register
    |    adder_out <= m_reg;
    |  end
    |
    |  // Output adder result
    |  assign accum_out = adder_out;
    |
    | endmodule
    """.stripMargin)
}


class MACC(WIDTH: Int, bypass: Boolean = true) extends Module {
  val io = IO(new Bundle(){
    val mult1 = Input(SInt(WIDTH.W))
    val mult2 = Input(SInt(WIDTH.W))
    val add = Input(SInt((2*WIDTH).W))
    val res = Output(SInt((2*WIDTH+1).W))
  })
  private val macc = Module(new MultAdd_3input(WIDTH, true))
  macc.io.clk <> this.clock.asUInt
  macc.io.rst <> this.reset.asUInt
  macc.io.en <> true.B
  macc.io.a <> io.mult1
  macc.io.b <> io.mult2
  macc.io.c <> io.add
  macc.io.p <> io.res
}

class MultAdd_3input(WIDTH: Int, bypass: Boolean = true) extends BlackBox(Map(
  "AWIDTH" -> WIDTH, // Width of multiplier's 1st input
  "BWIDTH" -> WIDTH, // Width of multiplier's 2nd input
  "CWIDTH" -> 2*WIDTH, // Width of Adder input
  "PWIDTH" -> (2*WIDTH + 1), // Output Width
)) with HasBlackBoxInline {
  val io = IO(new Bundle(){
    val clk = Input(Bool())
    val rst = Input(Bool())
    val en = Input(Bool())
    val a = Input(SInt(WIDTH.W))
    val b = Input(SInt(WIDTH.W))
    val c = Input(SInt((2*WIDTH).W))
    val p = Output(SInt((2*WIDTH+1).W))
  })

  if (bypass) {
    setInline("MultAdd_3input.v",
      s"""
        |  // This module describes a Multiplier,3 input adder (a*b + c + p(feedback))
        |  // This can be packed into 1 DSP block (Ultrascale architecture)
        |  // Make sure the widths are less than what is supported by the architecture
        | module MultAdd_3input #(
        |  parameter AWIDTH = 16,  // Width of multiplier's 1st input
        |  parameter BWIDTH = 16,  // Width of multiplier's 2nd input
        |  parameter CWIDTH = 32,  // Width of Adder input
        |  parameter PWIDTH = 33   // Output Width
        | ) (
        |  input clk, // Clock
        |  input rst, // Reset
        |  input en, // Reg enable
        |  input signed [AWIDTH-1:0] a, // Multiplier input
        |  input signed [BWIDTH-1:0] b, // Mutiplier input
        |  input signed [CWIDTH-1:0] c, // Adder input
        | output signed [PWIDTH-1:0] p// Result
        | );
        |
        |  wire signed [AWIDTH-1:0] a_r; // Multiplier input
        |  wire signed [BWIDTH-1:0] b_r; // Mutiplier input
        |  wire signed [CWIDTH-1:0] c_r; // Adder input
        |  wire signed [PWIDTH-1:0] p_r; // Result
        |
        |  assign a_r = a;
        |  assign b_r = b;
        |  assign c_r = c;
        |  assign p_r = a_r * b_r + c_r;
        |  assign p = p_r;
        | endmodule
  """.stripMargin)
  } else {
    setInline("MultAdd_3input.v",
      s"""
        |  // This module describes a Multiplier,3 input adder (a*b + c + p(feedback))
        |  // This can be packed into 1 DSP block (Ultrascale architecture)
        |  // Make sure the widths are less than what is supported by the architecture
        | module MultAdd-3input #(
        |  parameter AWIDTH = 16,  // Width of multiplier's 1st input
        |  parameter BWIDTH = 16,  // Width of multiplier's 2nd input
        |  parameter CWIDTH = 32,  // Width of Adder input
        |  parameter PWIDTH = 33   // Output Width
        | ) (
        |  input clk, // Clock
        |  input rst, // Reset
        |  input en,  // Reg enable
        |  input signed [AWIDTH-1:0] a, // Multiplier input
        |  input signed [BWIDTH-1:0] b, // Mutiplier input
        |  input signed [CWIDTH-1:0] c, // Adder input
        |  output signed [PWIDTH-1:0] p // Result
        | );
        |
        |  reg signed [AWIDTH-1:0] a_r; // Multiplier input
        |  reg signed [BWIDTH-1:0] b_r; // Mutiplier input
        |  reg signed [CWIDTH-1:0] c_r; // Adder input
        |  reg signed [PWIDTH-1:0] p_r; // Result
        |
        |  always @ (posedge clk)
        |  begin
        |   if(rst)
        |   begin
        |    a_r <= 0;
        |    b_r <= 0;
        |    c_r <= 0;
        |    p_r <= 0;
        |   end
        |   else
        |    begin
        |     if(en)
        |     begin
        |      a_r <= a;
        |      b_r <= b;
        |      c_r <= c;
        |      p_r <= a_r * b_r + c_r + p_r;
        |     end
        |    end
        |  end
        |  assign p = p_r;
        | endmodule
  """.stripMargin)
  }
  // */
}
