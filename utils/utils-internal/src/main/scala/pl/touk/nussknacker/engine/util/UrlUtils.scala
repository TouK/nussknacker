package pl.touk.nussknacker.engine.util

import java.io.File
import java.net.URL

object UrlUtils {

  implicit class ExpandFiles(val url: URL) extends AnyVal {

    def expandFiles(extension: String): List[URL] = {
      url match {
        case u if u.getProtocol.toLowerCase == "file" =>
          val file = new File(u.toURI)
          if (file.isDirectory) {
            val expanded = file
              .listFiles()
              .toList
              .filterNot(_.getName.startsWith("."))
              .map(_.toURI.toURL)
              .flatMap(_.expandFiles(extension))

            expanded match {
              case Nil                                                        => List.empty
              case nonEmpty if nonEmpty.exists(_.getFile.endsWith(extension)) => expanded
              case _                                                          => u :: Nil
            }
          } else {
            u :: Nil
          }
        case u =>
          u :: Nil
      }
    }

  }

}
