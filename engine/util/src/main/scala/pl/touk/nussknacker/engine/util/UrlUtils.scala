package pl.touk.nussknacker.engine.util

object UrlUtils {

  def parseHostAndPort(url: String): (String, Int) = {
    val parts = url.split(':')
    if (parts.length != 2)
      throw new IllegalArgumentException("Should by in host:port format")
    (parts(0), parts(1).toInt)
  }

}
