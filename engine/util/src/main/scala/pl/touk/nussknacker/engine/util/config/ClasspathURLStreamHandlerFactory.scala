package pl.touk.nussknacker.engine.util.config

import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}

object ClasspathURLStreamHandlerFactory extends URLStreamHandlerFactory {
  // Change to java.net.spi.URLStreamHandlerProvider when migrated to Java 9+
  URL.setURLStreamHandlerFactory(this)

  def createURLStreamHandler(protocol: String): URLStreamHandler =
    if (protocol == "classpath") new URLStreamHandler {
      def openConnection(url: URL): URLConnection = getClass.getClassLoader.getResource(url.getPath).openConnection()
    } else null

}
