package pl.touk.nussknacker.ui.listener

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

trait ListenerManagementFactory {
  def create(config: Config): ListenerManagement
}

object ListenerManagementFactory extends LazyLogging {
  def serviceLoader(classLoader: ClassLoader): ListenerManagementFactory = {
    aggregate(ScalaServiceLoader.load[ListenerManagementFactory](classLoader): _*)
  }

  def aggregate(factories: ListenerManagementFactory*): ListenerManagementFactory = new ListenerManagementFactory {
    override def create(config: Config): ListenerManagement = {
      val listeners = factories.map(_.create(config))
      new ListenerManagement {
        override def handler(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: LoggedUser): Unit = {
          def handleSafely(listener: ListenerManagement): Unit = {
            try {
              listener.handler(event)
            } catch {
              case ex: Throwable => logger.error(s"Error while handling event $event by listener ${listener.getClass.getName}", ex)
            }
          }
          listeners.foreach(handleSafely)
        }
      }
    }
  }
}

