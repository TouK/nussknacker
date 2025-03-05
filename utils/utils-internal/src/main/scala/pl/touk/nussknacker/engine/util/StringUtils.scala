package pl.touk.nussknacker.engine.util

import java.net.{URI, URL}
import java.nio.file.Path

object StringUtils {

  implicit class ToUrl(val value: String) extends AnyVal {

    def convertToURL(workingDirectoryOpt: Option[Path] = None): URL = {
      val uri = new URI(value)
      if (uri.isAbsolute) {
        uri.toURL
      } else {
        val pathPart = uri.getSchemeSpecificPart
        val path = workingDirectoryOpt.map { workingDirectory =>
          workingDirectory.resolve(pathPart)
        } getOrElse {
          Path.of(pathPart)
        }
        path.toUri.toURL
      }
    }

  }

}
