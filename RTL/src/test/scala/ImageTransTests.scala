package imageTrans

import org.scalatest._

class ImageTransformDriver(val hight: Int, val width: Int) {

  def getPicStream(path:String) = getClass.getResourceAsStream(path)
  
  def openFile(path: String): Array[Byte] = {
    val stream = getClass.getResourceAsStream(path)
    val byteArray = Stream.continually(stream.read())
      .takeWhile(_ != -1).map(_.toByte).toArray
    stream.close
    byteArray
  }

  def matches(img: Array[Byte], path: String) : Boolean = { 
    val imgRes = openFile(path) 
    return img.deep == imgRes.deep
  }

  /* Example of accessing bytewise in scala 
   * Assumes each pixel color is a single byte
   */
  def exampleBytewise(pathBase: String, pathRes: String, colorSel: Int): Boolean = {
    val imgBase: Array[Byte] = openFile(pathBase)
    val imgExpectedRes: Array[Byte] = openFile(pathRes)
 
    val ENTRY_SIZE = 3 // BYTES_PER_COLOR * 3 ??  R + G + B
    val imgRes = (for (pixel <- 0 until width * hight) yield {
      val getR: Byte = if((colorSel & (1 << 2)) == 0) 0.toByte else imgBase(pixel * ENTRY_SIZE + 0)
      val getG: Byte = if((colorSel & (1 << 1)) == 0) 0.toByte else imgBase(pixel * ENTRY_SIZE + 1)
      val getB: Byte = if((colorSel & (1 << 0)) == 0) 0.toByte else imgBase(pixel * ENTRY_SIZE + 2)
      Array(getR, getG, getB)
    }).flatten.toArray

    println("ImgBase size:" + imgBase.size + " | ImgRes size:" + imgRes.size)
    return imgRes.deep == imgExpectedRes.deep
  }

  def selRGB(selR: Int, selG: Int, selB: Int): Int = 
    (selR << 2) | (selG << 1) | (selB << 0)


}


class ImageProcSpec extends FlatSpec with Matchers {

  "An Image" should "be filtered by Red" in {
    val imgDriver = new ImageTransformDriver(480, 320)
    assert(imgDriver.exampleBytewise("/frame", "/frame_no_red", imgDriver.selRGB(0,1,1)))
  }
}
