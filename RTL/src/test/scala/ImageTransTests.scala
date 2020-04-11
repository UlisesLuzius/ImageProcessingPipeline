package imageTrans

import org.scalatest._

class ImageTransformDriver(
                            width: Int,
                            hight: Int,
                            imgPath: String,
                            resPath: String
                          ) {

  /* Example of accessing bytewis ins scala */
  def exampleBytewise: Boolean = {
    val stream = getClass.getResourceAsStream(imgPath)
    val imgBytes = Stream.continually(stream.read()).takeWhile(_ != -1).map(_.toByte).toArray

    val ENTRY_SIZE = 3 // BYTES_PER_COLOR * 3 ??  R + G + B

    val npArray = (for (i <- 0 until width * hight) yield {
      val getR = imgBytes(i * ENTRY_SIZE)
      val getG = imgBytes(i * ENTRY_SIZE + 1)
      val getB = imgBytes(i * ENTRY_SIZE + 2)
      Array(0.toByte, getG, getB)
    }).flatten.toArray

    val stream_res = getClass.getResourceAsStream(resPath)
    val imgBytes_res = Stream.continually(stream_res.read()).takeWhile(_ != -1).map(_.toByte).toArray
    
    return npArray.deep == imgBytes_res.deep
  }
}


class ImageProcSpec extends FlatSpec with Matchers {

  "An Image" should "be filtered by Red" in {
    val imgDriver = new ImageTransformDriver(
      480,
      320,
      "/frame",
      "/frame_no_red"
    )
    assert(imgDriver.exampleBytewise)
  }
}
