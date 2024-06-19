package pl.touk.nussknacker.ui.util

import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer

object IterableExtensions {

  implicit class Chunked(val strings: Iterable[String]) extends AnyVal {

    def groupByMaxChunkSize(maxChunkSizeInBytes: Int): List[List[String]] = {
      val (_, stringsAcc, resultAcc) = strings
        .foldLeft((0, ArrayBuffer[String](), ArrayBuffer[List[String]]())) {
          case ((bytesSizeAcc, elementsAcc, resultAcc), string) =>
            val stringSizeInBytes = string.getBytes(StandardCharsets.UTF_8).length
            val newBytesSizeAcc   = bytesSizeAcc + stringSizeInBytes
            if (newBytesSizeAcc > maxChunkSizeInBytes) {
              (
                stringSizeInBytes,
                ArrayBuffer[String](string),
                resultAcc :+ elementsAcc.toList
              )
            } else {
              (
                newBytesSizeAcc,
                elementsAcc :+ string,
                resultAcc
              )
            }
        }
      (resultAcc :+ stringsAcc.toList)
        .filter(_.nonEmpty)
        .toList
    }

  }

}
