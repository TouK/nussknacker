package pl.touk.nussknacker.openapi.http.backend

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.MetaData

import java.net
import scala.util.control.NonFatal

object ShutdownDetector extends LazyLogging {

  def startCheckerThread(metaData: MetaData,
                         checkClosed: () => Boolean,
                         closeAction: () => Unit): Thread = {
    val thread = new Thread() {
      override def run(): Unit = {
        while (!Thread.interrupted() && !checkClosed() && !checkClassloaderClosed()) {
          try {
            Thread.sleep(10000)
          } catch {
            case _: InterruptedException =>
              logger.info("Interrupt, exiting loop")
          }
          logger.debug("Checking if closed... ")
        }
        if (!checkClosed()) {
          logger.info("Classloader closing detected")
          closeAction()
        }
      }
    }
    thread.setDaemon(true)
    thread.setName(s"CacheableHttpClientCheck-${metaData.id}")
    thread.start()
    thread
  }

  //To jest *straszny* hack i pewnie w javie 11 nie bedzie dzialac - trzeba pomyslec nad lepszym sposobem...
  private def checkClassloaderClosed(): Boolean = {
    val loader = getClass.getClassLoader
    if (loader.isInstanceOf[net.URLClassLoader]) {
      try {
        val field = classOf[net.URLClassLoader].getDeclaredField("ucp")
        field.setAccessible(true)
        val urlClassPath = field.get(loader)
        val closedField = urlClassPath.getClass.getDeclaredField("closed")
        closedField.setAccessible(true)
        closedField.get(urlClassPath).asInstanceOf[Boolean]
      } catch {
        case NonFatal(e) =>
          logger.debug("Failed to detect if classloader closed", e)
          false
      }
    } else false

  }

}
