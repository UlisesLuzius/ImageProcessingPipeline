package imageTrans

import org.scalatest._

import java.io._
import java.nio.file.{Files, Paths}

class ImageTransformDriver (
  width: Int,
  hight: Int,
  imgPath: String,
  resPath: String
) {

  /* Example of accessing bytewis ins scala */
  def exampleBytewise: Boolean = {
    val imgBytes: Array[Byte] = Files.readAllBytes(Paths.get(imgPath))
    val ENTRY_SIZE = 6 // 2 BYTES_PER_COLOR * 3 ??  R + G + B
    val npArray = for(i <- 0 until width*hight) yield {
      val getR = imgBytes.slice(i*ENTRY_SIZE + 0, i*ENTRY_SIZE + 0 + 2)
      val getG = imgBytes.slice(i*ENTRY_SIZE + 2, i*ENTRY_SIZE + 2 + 2)
      val getB = imgBytes.slice(i*ENTRY_SIZE + 4, i*ENTRY_SIZE + 4 + 2)
      Array(getR ++ getG ++ getB)
    }
    print("Did something useless and buggy")
    return true
  }
}



class ImageProcSpec extends FlatSpec with Matchers {

  "An Image" should "be filtered by Red" in {
    val imgDriver = new ImageTransformDriver (
      240,
      333,
      "/path/to/your/image",
      "/path/to/your/imageTranformed"
      )
    assert(imgDriver.exampleBytewise)
  }
}
