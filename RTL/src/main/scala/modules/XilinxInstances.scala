package modules

import chisel3._
import chisel3.experimental._
import chisel3.util._



// XPM_FIFO instantiation template for Synchronous FIFO configurations
// Refer to the targeted device family architecture libraries guide for XPM_FIFO documentation
// =======================================================================================================================

// Parameter usage table, organized as follows:
// +---------------------------------------------------------------------------------------------------------------------+
// | Parameter name       | Data type          | Restrictions, if applicable                                             |
// |---------------------------------------------------------------------------------------------------------------------|
// | Description                                                                                                         |
// +---------------------------------------------------------------------------------------------------------------------+
// +---------------------------------------------------------------------------------------------------------------------+
// | DOUT_RESET_VALUE     | String             | Default value = 0.                                                      |
// |---------------------------------------------------------------------------------------------------------------------|
// | Reset value of read data path.                                                                                      |
// +---------------------------------------------------------------------------------------------------------------------+
// | ECC_MODE             | String             | Allowed values: no_ecc, en_ecc. Default value = no_ecc.                 |
// |---------------------------------------------------------------------------------------------------------------------|
// |                                                                                                                     |
// |   "no_ecc" - Disables ECC                                                                                           |
// |   "en_ecc" - Enables both ECC Encoder and Decoder                                                                   |
// |                                                                                                                     |
// | NOTE: ECC_MODE should be "no_ecc" if FIFO_MEMORY_TYPE is set to "auto". Violating this may result incorrect behavior.|
// +---------------------------------------------------------------------------------------------------------------------+
// | FIFO_MEMORY_TYPE     | String             | Allowed values: auto, block, distributed, ultra. Default value = auto.  |
// |---------------------------------------------------------------------------------------------------------------------|
// | Designate the fifo memory primitive (resource type) to use-                                                         |
// |                                                                                                                     |
// |   "auto"- Allow Vivado Synthesis to choose                                                                          |
// |   "block"- Block RAM FIFO                                                                                           |
// |   "distributed"- Distributed RAM FIFO                                                                               |
// |   "ultra"- URAM FIFO                                                                                                |
// |                                                                                                                     |
// | NOTE: There may be a behavior mismatch if Block RAM or Ultra RAM specific features, like ECC or Asymmetry, are selected with FIFO_MEMORY_TYPE set to "auto".|
// +---------------------------------------------------------------------------------------------------------------------+
// | FIFO_READ_LATENCY    | Integer            | Range: 0 - 100. Default value = 1.                                      |
// |---------------------------------------------------------------------------------------------------------------------|
// | Number of output register stages in the read data path                                                              |
// |                                                                                                                     |
// |   If READ_MODE = "fwft", then the only applicable value is 0                                                        |
// +---------------------------------------------------------------------------------------------------------------------+
// | FIFO_WRITE_DEPTH     | Integer            | Range: 16 - 4194304. Default value = 2048.                              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Defines the FIFO Write Depth, must be power of two                                                                  |
// |                                                                                                                     |
// |   In standard READ_MODE, the effective depth = FIFO_WRITE_DEPTH                                                     |
// |   In First-Word-Fall-Through READ_MODE, the effective depth = FIFO_WRITE_DEPTH+2                                    |
// |                                                                                                                     |
// | NOTE: The maximum FIFO size (width x depth) is limited to 150-Megabits.                                             |
// +---------------------------------------------------------------------------------------------------------------------+
// | FULL_RESET_VALUE     | Integer            | Range: 0 - 1. Default value = 0.                                        |
// |---------------------------------------------------------------------------------------------------------------------|
// | Sets full, almost_full and prog_full to FULL_RESET_VALUE during reset                                               |
// +---------------------------------------------------------------------------------------------------------------------+
// | PROG_EMPTY_THRESH    | Integer            | Range: 3 - 4194304. Default value = 10.                                 |
// |---------------------------------------------------------------------------------------------------------------------|
// | Specifies the minimum number of read words in the FIFO at or below which prog_empty is asserted.                    |
// |                                                                                                                     |
// |   Min_Value = 3 + (READ_MODE_VAL*2)                                                                                 |
// |   Max_Value = (FIFO_WRITE_DEPTH-3) - (READ_MODE_VAL*2)                                                              |
// |                                                                                                                     |
// | If READ_MODE = "std", then READ_MODE_VAL = 0; Otherwise READ_MODE_VAL = 1.                                          |
// | NOTE: The default threshold value is dependent on default FIFO_WRITE_DEPTH value. If FIFO_WRITE_DEPTH value is      |
// | changed, ensure the threshold value is within the valid range though the programmable flags are not used.           |
// +---------------------------------------------------------------------------------------------------------------------+
// | PROG_FULL_THRESH     | Integer            | Range: 3 - 4194301. Default value = 10.                                 |
// |---------------------------------------------------------------------------------------------------------------------|
// | Specifies the maximum number of write words in the FIFO at or above which prog_full is asserted.                    |
// |                                                                                                                     |
// |   Min_Value = 3 + (READ_MODE_VAL*2*(FIFO_WRITE_DEPTH/FIFO_READ_DEPTH))                                              |
// |   Max_Value = (FIFO_WRITE_DEPTH-3) - (READ_MODE_VAL*2*(FIFO_WRITE_DEPTH/FIFO_READ_DEPTH))                           |
// |                                                                                                                     |
// | If READ_MODE = "std", then READ_MODE_VAL = 0; Otherwise READ_MODE_VAL = 1.                                          |
// | NOTE: The default threshold value is dependent on default FIFO_WRITE_DEPTH value. If FIFO_WRITE_DEPTH value is      |
// | changed, ensure the threshold value is within the valid range though the programmable flags are not used.           |
// +---------------------------------------------------------------------------------------------------------------------+
// | RD_DATA_COUNT_WIDTH  | Integer            | Range: 1 - 23. Default value = 1.                                       |
// |---------------------------------------------------------------------------------------------------------------------|
// | Specifies the width of rd_data_count. To reflect the correct value, the width should be log2(FIFO_READ_DEPTH)+1.    |
// |                                                                                                                     |
// |   FIFO_READ_DEPTH = FIFO_WRITE_DEPTH*WRITE_DATA_WIDTH/READ_DATA_WIDTH                                               |
// +---------------------------------------------------------------------------------------------------------------------+
// | READ_DATA_WIDTH      | Integer            | Range: 1 - 4096. Default value = 32.                                    |
// |---------------------------------------------------------------------------------------------------------------------|
// | Defines the width of the read data port, dout                                                                       |
// |                                                                                                                     |
// |   Write and read width aspect ratio must be 1:1, 1:2, 1:4, 1:8, 8:1, 4:1 and 2:1                                    |
// |   For example, if WRITE_DATA_WIDTH is 32, then the READ_DATA_WIDTH must be 32, 64,128, 256, 16, 8, 4.               |
// |                                                                                                                     |
// | NOTE:                                                                                                               |
// |                                                                                                                     |
// |   READ_DATA_WIDTH should be equal to WRITE_DATA_WIDTH if FIFO_MEMORY_TYPE is set to "auto". Violating this may result incorrect behavior. |
// |   The maximum FIFO size (width x depth) is limited to 150-Megabits.                                                 |
// +---------------------------------------------------------------------------------------------------------------------+
// | READ_MODE            | String             | Allowed values: std, fwft. Default value = std.                         |
// |---------------------------------------------------------------------------------------------------------------------|
// |                                                                                                                     |
// |   "std"- standard read mode                                                                                         |
// |   "fwft"- First-Word-Fall-Through read mode                                                                         |
// +---------------------------------------------------------------------------------------------------------------------+
// | SIM_ASSERT_CHK       | Integer            | Range: 0 - 1. Default value = 0.                                        |
// |---------------------------------------------------------------------------------------------------------------------|
// | 0- Disable simulation message reporting. Messages related to potential misuse will not be reported.                 |
// | 1- Enable simulation message reporting. Messages related to potential misuse will be reported.                      |
// +---------------------------------------------------------------------------------------------------------------------+
// | USE_ADV_FEATURES     | String             | Default value = 0707.                                                   |
// |---------------------------------------------------------------------------------------------------------------------|
// | Enables data_valid, almost_empty, rd_data_count, prog_empty, underflow, wr_ack, almost_full, wr_data_count,         |
// | prog_full, overflow features.                                                                                       |
// |                                                                                                                     |
// |   Setting USE_ADV_FEATURES[0] to 1 enables overflow flag;     Default value of this bit is 1                        |
// |   Setting USE_ADV_FEATURES[1]  to 1 enables prog_full flag;    Default value of this bit is 1                       |
// |   Setting USE_ADV_FEATURES[2]  to 1 enables wr_data_count;     Default value of this bit is 1                       |
// |   Setting USE_ADV_FEATURES[3]  to 1 enables almost_full flag;  Default value of this bit is 0                       |
// |   Setting USE_ADV_FEATURES[4]  to 1 enables wr_ack flag;       Default value of this bit is 0                       |
// |   Setting USE_ADV_FEATURES[8]  to 1 enables underflow flag;    Default value of this bit is 1                       |
// |   Setting USE_ADV_FEATURES[9]  to 1 enables prog_empty flag;   Default value of this bit is 1                       |
// |   Setting USE_ADV_FEATURES[10] to 1 enables rd_data_count;     Default value of this bit is 1                       |
// |   Setting USE_ADV_FEATURES[11] to 1 enables almost_empty flag; Default value of this bit is 0                       |
// |   Setting USE_ADV_FEATURES[12] to 1 enables data_valid flag;   Default value of this bit is 0                       |
// +---------------------------------------------------------------------------------------------------------------------+
// | WAKEUP_TIME          | Integer            | Range: 0 - 2. Default value = 0.                                        |
// |---------------------------------------------------------------------------------------------------------------------|
// |                                                                                                                     |
// |   0 - Disable sleep                                                                                                 |
// |   2 - Use Sleep Pin                                                                                                 |
// |                                                                                                                     |
// | NOTE: WAKEUP_TIME should be 0 if FIFO_MEMORY_TYPE is set to "auto". Violating this may result incorrect behavior.   |
// +---------------------------------------------------------------------------------------------------------------------+
// | WRITE_DATA_WIDTH     | Integer            | Range: 1 - 4096. Default value = 32.                                    |
// |---------------------------------------------------------------------------------------------------------------------|
// | Defines the width of the write data port, din                                                                       |
// |                                                                                                                     |
// |   Write and read width aspect ratio must be 1:1, 1:2, 1:4, 1:8, 8:1, 4:1 and 2:1                                    |
// |   For example, if WRITE_DATA_WIDTH is 32, then the READ_DATA_WIDTH must be 32, 64,128, 256, 16, 8, 4.               |
// |                                                                                                                     |
// | NOTE:                                                                                                               |
// |                                                                                                                     |
// |   WRITE_DATA_WIDTH should be equal to READ_DATA_WIDTH if FIFO_MEMORY_TYPE is set to "auto". Violating this may result incorrect behavior.|
// |   The maximum FIFO size (width x depth) is limited to 150-Megabits.                                                 |
// +---------------------------------------------------------------------------------------------------------------------+
// | WR_DATA_COUNT_WIDTH  | Integer            | Range: 1 - 23. Default value = 1.                                       |
// |---------------------------------------------------------------------------------------------------------------------|
// | Specifies the width of wr_data_count. To reflect the correct value, the width should be log2(FIFO_WRITE_DEPTH)+1.   |
// +---------------------------------------------------------------------------------------------------------------------+

// Port usage table, organized as follows:
// +---------------------------------------------------------------------------------------------------------------------+
// | Port name      | Direction | Size, in bits                         | Domain  | Sense       | Handling if unused     |
// |---------------------------------------------------------------------------------------------------------------------|
// | Description                                                                                                         |
// +---------------------------------------------------------------------------------------------------------------------+
// +---------------------------------------------------------------------------------------------------------------------+
// | almost_empty   | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Almost Empty : When asserted, this signal indicates that only one more read can be performed before the FIFO goes to|
// | empty.                                                                                                              |
// +---------------------------------------------------------------------------------------------------------------------+
// | almost_full    | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Almost Full: When asserted, this signal indicates that only one more write can be performed before the FIFO is full.|
// +---------------------------------------------------------------------------------------------------------------------+
// | data_valid     | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Read Data Valid: When asserted, this signal indicates that valid data is available on the output bus (dout).        |
// +---------------------------------------------------------------------------------------------------------------------+
// | dbiterr        | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Double Bit Error: Indicates that the ECC decoder detected a double-bit error and data in the FIFO core is corrupted.|
// +---------------------------------------------------------------------------------------------------------------------+
// | din            | Input     | WRITE_DATA_WIDTH                      | wr_clk  | NA          | Required               |
// |---------------------------------------------------------------------------------------------------------------------|
// | Write Data: The input data bus used when writing the FIFO.                                                          |
// +---------------------------------------------------------------------------------------------------------------------+
// | dout           | Output    | READ_DATA_WIDTH                       | wr_clk  | NA          | Required               |
// |---------------------------------------------------------------------------------------------------------------------|
// | Read Data: The output data bus is driven when reading the FIFO.                                                     |
// +---------------------------------------------------------------------------------------------------------------------+
// | empty          | Output    | 1                                     | wr_clk  | Active-high | Required               |
// |---------------------------------------------------------------------------------------------------------------------|
// | Empty Flag: When asserted, this signal indicates that the FIFO is empty.                                            |
// | Read requests are ignored when the FIFO is empty, initiating a read while empty is not destructive to the FIFO.     |
// +---------------------------------------------------------------------------------------------------------------------+
// | full           | Output    | 1                                     | wr_clk  | Active-high | Required               |
// |---------------------------------------------------------------------------------------------------------------------|
// | Full Flag: When asserted, this signal indicates that the FIFO is full.                                              |
// | Write requests are ignored when the FIFO is full, initiating a write when the FIFO is full is not destructive       |
// | to the contents of the FIFO.                                                                                        |
// +---------------------------------------------------------------------------------------------------------------------+
// | injectdbiterr  | Input     | 1                                     | wr_clk  | Active-high | Tie to 1'b0            |
// |---------------------------------------------------------------------------------------------------------------------|
// | Double Bit Error Injection: Injects a double bit error if the ECC feature is used on block RAMs or                  |
// | UltraRAM macros.                                                                                                    |
// +---------------------------------------------------------------------------------------------------------------------+
// | injectsbiterr  | Input     | 1                                     | wr_clk  | Active-high | Tie to 1'b0            |
// |---------------------------------------------------------------------------------------------------------------------|
// | Single Bit Error Injection: Injects a single bit error if the ECC feature is used on block RAMs or                  |
// | UltraRAM macros.                                                                                                    |
// +---------------------------------------------------------------------------------------------------------------------+
// | overflow       | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Overflow: This signal indicates that a write request (wren) during the prior clock cycle was rejected,              |
// | because the FIFO is full. Overflowing the FIFO is not destructive to the contents of the FIFO.                      |
// +---------------------------------------------------------------------------------------------------------------------+
// | prog_empty     | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Programmable Empty: This signal is asserted when the number of words in the FIFO is less than or equal              |
// | to the programmable empty threshold value.                                                                          |
// | It is de-asserted when the number of words in the FIFO exceeds the programmable empty threshold value.              |
// +---------------------------------------------------------------------------------------------------------------------+
// | prog_full      | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Programmable Full: This signal is asserted when the number of words in the FIFO is greater than or equal            |
// | to the programmable full threshold value.                                                                           |
// | It is de-asserted when the number of words in the FIFO is less than the programmable full threshold value.          |
// +---------------------------------------------------------------------------------------------------------------------+
// | rd_data_count  | Output    | RD_DATA_COUNT_WIDTH                   | wr_clk  | NA          | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Read Data Count: This bus indicates the number of words read from the FIFO.                                         |
// +---------------------------------------------------------------------------------------------------------------------+
// | rd_en          | Input     | 1                                     | wr_clk  | Active-high | Required               |
// |---------------------------------------------------------------------------------------------------------------------|
// | Read Enable: If the FIFO is not empty, asserting this signal causes data (on dout) to be read from the FIFO.        |
// |                                                                                                                     |
// |   Must be held active-low when rd_rst_busy is active high.                                                          |
// +---------------------------------------------------------------------------------------------------------------------+
// | rd_rst_busy    | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Read Reset Busy: Active-High indicator that the FIFO read domain is currently in a reset state.                     |
// +---------------------------------------------------------------------------------------------------------------------+
// | rst            | Input     | 1                                     | wr_clk  | Active-high | Required               |
// |---------------------------------------------------------------------------------------------------------------------|
// | Reset: Must be synchronous to wr_clk. The clock(s) can be unstable at the time of applying reset, but reset must be released only after the clock(s) is/are stable.|
// +---------------------------------------------------------------------------------------------------------------------+
// | sbiterr        | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Single Bit Error: Indicates that the ECC decoder detected and fixed a single-bit error.                             |
// +---------------------------------------------------------------------------------------------------------------------+
// | sleep          | Input     | 1                                     | NA      | Active-high | Tie to 1'b0            |
// |---------------------------------------------------------------------------------------------------------------------|
// | Dynamic power saving- If sleep is High, the memory/fifo block is in power saving mode.                              |
// +---------------------------------------------------------------------------------------------------------------------+
// | underflow      | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Underflow: Indicates that the read request (rd_en) during the previous clock cycle was rejected                     |
// | because the FIFO is empty. Under flowing the FIFO is not destructive to the FIFO.                                   |
// +---------------------------------------------------------------------------------------------------------------------+
// | wr_ack         | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Write Acknowledge: This signal indicates that a write request (wr_en) during the prior clock cycle is succeeded.    |
// +---------------------------------------------------------------------------------------------------------------------+
// | wr_clk         | Input     | 1                                     | NA      | Rising edge | Required               |
// |---------------------------------------------------------------------------------------------------------------------|
// | Write clock: Used for write operation. wr_clk must be a free running clock.                                         |
// +---------------------------------------------------------------------------------------------------------------------+
// | wr_data_count  | Output    | WR_DATA_COUNT_WIDTH                   | wr_clk  | NA          | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Write Data Count: This bus indicates the number of words written into the FIFO.                                     |
// +---------------------------------------------------------------------------------------------------------------------+
// | wr_en          | Input     | 1                                     | wr_clk  | Active-high | Required               |
// |---------------------------------------------------------------------------------------------------------------------|
// | Write Enable: If the FIFO is not full, asserting this signal causes data (on din) to be written to the FIFO         |
// |                                                                                                                     |
// |   Must be held active-low when rst or wr_rst_busy or rd_rst_busy is active high                                     |
// +---------------------------------------------------------------------------------------------------------------------+
// | wr_rst_busy    | Output    | 1                                     | wr_clk  | Active-high | DoNotCare              |
// |---------------------------------------------------------------------------------------------------------------------|
// | Write Reset Busy: Active-High indicator that the FIFO write domain is currently in a reset state.                   |
// +---------------------------------------------------------------------------------------------------------------------+

object XPM_FIFO_CFG {
  def apply(): XPM_FIFO_CFG = new XPM_FIFO_CFG("0", "no_ecc", 
  "auto", 1, 2048, 0, 10, 10, 1, 32, "std", 0, "0707", 0, 32, 1)

}

class XPM_FIFO_CFG(
    val DOUT_RESET_VALUE: String = "0",    // String
    val ECC_MODE: String         = "no_ecc", // String
    val FIFO_MEMORY_TYPE: String = "auto",   // String
    val FIFO_READ_LATENCY: Int   = 1,      // DECIMAL
    val FIFO_WRITE_DEPTH: Int    = 2048,   // DECIMAL
    val FULL_RESET_VALUE: Int    = 0,      // DECIMAL
    val PROG_EMPTY_THRESH: Int   = 10,     // DECIMAL
    val PROG_FULL_THRESH: Int    = 10,     // DECIMAL
    val RD_DATA_COUNT_WIDTH: Int = 1,      // DECIMAL
    val READ_DATA_WIDTH: Int     = 32,     // DECIMAL
    val READ_MODE: String        = "std",  // String
    val SIM_ASSERT_CHK: Int      = 0,      // DECIMAL; 0=disable simulation messages, 1=enable simulation messages
    val USE_ADV_FEATURES: String = "0707", // String
    val WAKEUP_TIME: Int         = 0,      // DECIMAL
    val WRITE_DATA_WIDTH: Int    = 32,     // DECIMAL
    val WR_DATA_COUNT_WIDTH: Int = 1       // DECIMAL
)

class XPM_FIFO(val cfg: XPM_FIFO_CFG) extends BlackBox(Map(
  "DOUT_RESET_VALUE"    -> cfg.DOUT_RESET_VALUE,   // String
  "ECC_MODE"            -> cfg.ECC_MODE,           // String
  "FIFO_MEMORY_TYPE"    -> cfg.FIFO_MEMORY_TYPE,   // String
  "FIFO_READ_LATENCY"   -> cfg.FIFO_READ_LATENCY,  // DECIMAL
  "FIFO_WRITE_DEPTH"    -> cfg.FIFO_WRITE_DEPTH,   // DECIMAL
  "FULL_RESET_VALUE"    -> cfg.FULL_RESET_VALUE,   // DECIMAL
  "PROG_EMPTY_THRESH"   -> cfg.PROG_EMPTY_THRESH,  // DECIMAL
  "PROG_FULL_THRESH"    -> cfg.PROG_FULL_THRESH,   // DECIMAL
  "RD_DATA_COUNT_WIDTH" -> cfg.RD_DATA_COUNT_WIDTH, // DECIMAL
  "READ_DATA_WIDTH"     -> cfg.READ_DATA_WIDTH,     // DECIMAL
  "READ_MODE"           -> cfg.READ_MODE,           // String
  "SIM_ASSERT_CHK"      -> cfg.SIM_ASSERT_CHK,      // String
  "USE_ADV_FEATURES"    -> cfg.USE_ADV_FEATURES,    // String
  "WAKEUP_TIME"         -> cfg.WAKEUP_TIME,         // DECIMAL
  "WRITE_DATA_WIDTH"    -> cfg.WRITE_DATA_WIDTH,    // DECIMAL
  "WR_DATA_COUNT_WIDTH" -> cfg.WR_DATA_COUNT_WIDTH, // DECIMAL
)) with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clk = Input(Bool()) 
    val rst = Input(Bool())                    
    val almost_empty  = Output(Bool())                          
    val almost_full   = Output(Bool())                          
    val data_valid    = Output(Bool())                          
    val dbiterr       = Output(Bool())                          
    val dout          = Output(UInt(cfg.READ_DATA_WIDTH.W))
    val empty         = Output(Bool())                          
    val full          = Output(Bool())                          
    val overflow      = Output(Bool())                          
    val prog_empty    = Output(Bool())                          
    val prog_full     = Output(Bool())                          
    val rd_data_count = Output(UInt(cfg.RD_DATA_COUNT_WIDTH.W))
    val rd_rst_busy   = Output(Bool())                          
    val sbiterr       = Output(Bool())                          
    val underflow     = Output(Bool())                          
    val wr_ack        = Output(Bool())                          
    val wr_data_count = Output(UInt(cfg.WR_DATA_COUNT_WIDTH.W))
    val wr_rst_busy   = Output(Bool())                          
    val din           =  Input(UInt(cfg.WRITE_DATA_WIDTH.W))
    val injectdbiterr =  Input(Bool())                          
    val injectsbiterr =  Input(Bool())                          
    val rd_en         =  Input(Bool())                          
    val sleep         =  Input(Bool())                          
    val wr_clk        =  Input(Bool())                          
    val wr_en         =  Input(Bool())                          
  })

  setInline("xpm_fifo_sync.v",
    s"""
  | module XPM_FIFO #(
  |   parameter DOUT_RESET_VALUE = "0",    // String
  |   parameter ECC_MODE = "no_ecc",       // String
  |   parameter FIFO_MEMORY_TYPE = "auto", // String
  |   parameter FIFO_READ_LATENCY = 1,     // DECIMAL
  |   parameter FIFO_WRITE_DEPTH = 2048,   // DECIMAL
  |   parameter FULL_RESET_VALUE = 0,      // DECIMAL
  |   parameter PROG_EMPTY_THRESH = 10,    // DECIMAL
  |   parameter PROG_FULL_THRESH = 10,     // DECIMAL
  |   parameter RD_DATA_COUNT_WIDTH = 1,   // DECIMAL
  |   parameter READ_DATA_WIDTH = 32,      // DECIMAL
  |   parameter READ_MODE = "std",         // String
  |   parameter SIM_ASSERT_CHK = 0,        // DECIMAL; 0=disable simulation messages, 1=enable simulation messages
  |   parameter USE_ADV_FEATURES = "0707", // String
  |   parameter WAKEUP_TIME = 0,           // DECIMAL
  |   parameter WRITE_DATA_WIDTH = 32,     // DECIMAL
  |   parameter WR_DATA_COUNT_WIDTH = 1    // DECIMAL
  | ) (
  |   input clk,
  |   input rst,                   
  |   output                           almost_empty, 
  |   output                           almost_full,   
  |   output                           data_valid,     
  |   output                           dbiterr,           
  |   output [WRITE_DATA_WIDTH-1:0]    dout,                 
  |   output                           empty,               
  |   output                           full,                 
  |   output                           overflow,         
  |   output                           prog_empty,     
  |   output                           prog_full,       
  |   output [RD_DATA_COUNT_WIDTH-1:0] rd_data_count,
  |   output                           rd_rst_busy,   
  |   output                           sbiterr,           
  |   output                           underflow,       
  |   output                           wr_ack,             
  |   output [WR_DATA_COUNT_WIDTH-1:0] wr_data_count,
  |   output                           wr_rst_busy,   
  |   input  [READ_DATA_WIDTH-1:0]     din,                   
  |   input                            injectdbiterr,
  |   input                            injectsbiterr,
  |   input                            rd_en,               
  |   input                            sleep,                 
  |   input                            wr_clk,               
  |   input                            wr_en
  | );
  |  // xpm_fifo_sync : In order to incorporate this function into the design,
  |  //    Verilog    : the following instance declaration needs to be placed
  |  //   instance    : in the body of the design code.  The instance name
  |  //  declaration  : (xpm_fifo_sync_inst) and/or the port declarations within the
  |  //     code      : parenthesis may be changed to properly reference and
  |  //               : connect this function to the design.  All inputs
  |  //               : and outputs must be connected.
  |  
  |  //  Please reference the appropriate libraries guide for additional information on the XPM modules.
  |  
  |  //  <-----Cut code below this line---->
  |  
  |     // xpm_fifo_sync: Synchronous FIFO
  |     // Xilinx Parameterized Macro, version 2019.1
  |  
  |     xpm_fifo_sync #(
  |        .DOUT_RESET_VALUE(DOUT_RESET_VALUE),   // String
  |        .ECC_MODE(ECC_MODE),                   // String
  |        .FIFO_MEMORY_TYPE(FIFO_MEMORY_TYPE),   // String
  |        .FIFO_READ_LATENCY(FIFO_READ_LATENCY), // DECIMAL
  |        .FIFO_WRITE_DEPTH(FIFO_WRITE_DEPTH),   // DECIMAL
  |        .FULL_RESET_VALUE(FULL_RESET_VALUE),   // DECIMAL
  |        .PROG_EMPTY_THRESH(PROG_EMPTY_THRESH), // DECIMAL
  |        .PROG_FULL_THRESH(PROG_FULL_THRESH),   // DECIMAL
  |        .RD_DATA_COUNT_WIDTH(RD_DATA_COUNT_WIDTH), // DECIMAL
  |        .READ_DATA_WIDTH(READ_DATA_WIDTH),      // DECIMAL
  |        .READ_MODE(READ_MODE),                  // String
  |        .SIM_ASSERT_CHK(SIM_ASSERT_CHK),        // DECIMAL; 0=disable simulation messages, 1=enable simulation messages
  |        .USE_ADV_FEATURES(USE_ADV_FEATURES),    // String
  |        .WAKEUP_TIME(WAKEUP_TIME),              // DECIMAL
  |        .WRITE_DATA_WIDTH(WRITE_DATA_WIDTH),    // DECIMAL
  |        .WR_DATA_COUNT_WIDTH(WR_DATA_COUNT_WIDTH)  // DECIMAL
  |     )
  |     xpm_fifo_sync_inst (
  |        .almost_empty(almost_empty),   // 1-bit output: Almost Empty : When asserted, this signal indicates that
  |                                       // only one more read can be performed before the FIFO goes to empty.
  |  
  |        .almost_full(almost_full),     // 1-bit output: Almost Full: When asserted, this signal indicates that
  |                                       // only one more write can be performed before the FIFO is full.
  |  
  |        .data_valid(data_valid),       // 1-bit output: Read Data Valid: When asserted, this signal indicates
  |                                       // that valid data is available on the output bus (dout).
  |  
  |        .dbiterr(dbiterr),             // 1-bit output: Double Bit Error: Indicates that the ECC decoder detected
  |                                       // a double-bit error and data in the FIFO core is corrupted.
  |  
  |        .dout(dout),                   // READ_DATA_WIDTH-bit output: Read Data: The output data bus is driven
  |                                       // when reading the FIFO.
  |  
  |        .empty(empty),                 // 1-bit output: Empty Flag: When asserted, this signal indicates that the
  |                                       // FIFO is empty. Read requests are ignored when the FIFO is empty,
  |                                       // initiating a read while empty is not destructive to the FIFO.
  |  
  |        .full(full),                   // 1-bit output: Full Flag: When asserted, this signal indicates that the
  |                                       // FIFO is full. Write requests are ignored when the FIFO is full,
  |                                       // initiating a write when the FIFO is full is not destructive to the
  |                                       // contents of the FIFO.
  |  
  |        .overflow(overflow),           // 1-bit output: Overflow: This signal indicates that a write request
  |                                       // (wren) during the prior clock cycle was rejected, because the FIFO is
  |                                       // full. Overflowing the FIFO is not destructive to the contents of the
  |                                       // FIFO.
  |  
  |        .prog_empty(prog_empty),       // 1-bit output: Programmable Empty: This signal is asserted when the
  |                                       // number of words in the FIFO is less than or equal to the programmable
  |                                       // empty threshold value. It is de-asserted when the number of words in
  |                                       // the FIFO exceeds the programmable empty threshold value.
  |  
  |        .prog_full(prog_full),         // 1-bit output: Programmable Full: This signal is asserted when the
  |                                       // number of words in the FIFO is greater than or equal to the
  |                                       // programmable full threshold value. It is de-asserted when the number of
  |                                       // words in the FIFO is less than the programmable full threshold value.
  |  
  |        .rd_data_count(rd_data_count), // RD_DATA_COUNT_WIDTH-bit output: Read Data Count: This bus indicates the
  |                                       // number of words read from the FIFO.
  |  
  |        .rd_rst_busy(rd_rst_busy),     // 1-bit output: Read Reset Busy: Active-High indicator that the FIFO read
  |                                       // domain is currently in a reset state.
  |  
  |        .sbiterr(sbiterr),             // 1-bit output: Single Bit Error: Indicates that the ECC decoder detected
  |                                       // and fixed a single-bit error.
  |  
  |        .underflow(underflow),         // 1-bit output: Underflow: Indicates that the read request (rd_en) during
  |                                       // the previous clock cycle was rejected because the FIFO is empty. Under
  |                                       // flowing the FIFO is not destructive to the FIFO.
  |  
  |        .wr_ack(wr_ack),               // 1-bit output: Write Acknowledge: This signal indicates that a write
  |                                       // request (wr_en) during the prior clock cycle is succeeded.
  |  
  |        .wr_data_count(wr_data_count), // WR_DATA_COUNT_WIDTH-bit output: Write Data Count: This bus indicates
  |                                       // the number of words written into the FIFO.
  |  
  |        .wr_rst_busy(wr_rst_busy),     // 1-bit output: Write Reset Busy: Active-High indicator that the FIFO
  |                                       // write domain is currently in a reset state.
  |  
  |        .din(din),                     // WRITE_DATA_WIDTH-bit input: Write Data: The input data bus used when
  |                                       // writing the FIFO.
  |  
  |        .injectdbiterr(injectdbiterr), // 1-bit input: Double Bit Error Injection: Injects a double bit error if
  |                                       // the ECC feature is used on block RAMs or UltraRAM macros.
  |  
  |        .injectsbiterr(injectsbiterr), // 1-bit input: Single Bit Error Injection: Injects a single bit error if
  |                                       // the ECC feature is used on block RAMs or UltraRAM macros.
  |  
  |        .rd_en(rd_en),                 // 1-bit input: Read Enable: If the FIFO is not empty, asserting this
  |                                       // signal causes data (on dout) to be read from the FIFO. Must be held
  |                                       // active-low when rd_rst_busy is active high.
  |  
  |        .rst(rst),                     // 1-bit input: Reset: Must be synchronous to wr_clk. The clock(s) can be
  |                                       // unstable at the time of applying reset, but reset must be released only
  |                                       // after the clock(s) is/are stable.
  |  
  |        .sleep(sleep),                 // 1-bit input: Dynamic power saving- If sleep is High, the memory/fifo
  |                                       // block is in power saving mode.
  |  
  |        .wr_clk(wr_clk),               // 1-bit input: Write clock: Used for write operation. wr_clk must be a
  |                                       // free running clock.
  |  
  |        .wr_en(wr_en)                  // 1-bit input: Write Enable: If the FIFO is not full, asserting this
  |                                       // signal causes data (on din) to be written to the FIFO Must be held
  |                                       // active-low when rst or wr_rst_busy or rd_rst_busy is active high
  |  
  |     );
  |  
  |     // End of xpm_fifo_sync_inst instantiation
  | endmodule;
""")
}
