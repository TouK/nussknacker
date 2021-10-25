package pl.touk.nussknacker.engine.util.loader

import java.net.URLClassLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}

import java.io.File

trait LoadClassFromClassLoader {
  type ClassToLoad
  val prettyClassName: String

  def loadAll(classLoader: ClassLoader): List[ClassToLoad]

  def justOne(classLoader: ClassLoader): ClassToLoad = {
    Multiplicity(loadAll(classLoader)) match {
      case Empty() =>
        throw new IllegalArgumentException(s"$prettyClassName not found. ${jarsUrlsHint(classLoader)}")
      case Many(muchEntities) =>
        throw new IllegalArgumentException(s"Many $prettyClassName implementations found. Classes found: $muchEntities. ${jarsUrlsHint(classLoader)}")
      case One(only) => only
    }
  }

  private def jarsUrlsHint(classLoader: ClassLoader) = {
    classLoader match {
      case cl: URLClassLoader =>
        val urls = cl.getURLs.toList
        val missingFiles = urls.collect {
          case url if url.getProtocol == "file" && !new File(url.toURI).exists() => url.getFile
        }
        s"Jar URLs configured: ${urls.mkString(", ")}, missing files: ${missingFiles.mkString(", ")}"
      case _ =>
        ""
    }
  }
}
