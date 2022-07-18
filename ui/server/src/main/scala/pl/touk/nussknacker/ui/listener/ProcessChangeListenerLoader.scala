package pl.touk.nussknacker.ui.listener

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.listener.services.NussknackerServices

import scala.concurrent.ExecutionContext

object ProcessChangeListenerLoader extends LazyLogging {

  def loadListeners(classLoader: ClassLoader,
                    config: Config,
                    services: NussknackerServices, predefined: ProcessChangeListener*): ProcessChangeListener = {
    val factories = ScalaServiceLoader.load[ProcessChangeListenerFactory](classLoader)
    logger.info(s"Loading listener factories: ${factories.map(_.getClass.getCanonicalName)}")
    val listeners = factories.map(_.create(config, services))
    new ProcessChangeListenerAggregate(predefined.toList ::: listeners)
  }

  class ProcessChangeListenerAggregate(listeners: Seq[ProcessChangeListener]) extends ProcessChangeListener {
    override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = {
      def handleSafely(listener: ProcessChangeListener): Unit = {
        try {
          listener.handle(event)
        } catch {
          case ex: Throwable => logger.error(s"Error while handling event $event by listener ${listener.getClass.getName}", ex)
        }
      }
      listeners.foreach(handleSafely)
    }
  }

}
