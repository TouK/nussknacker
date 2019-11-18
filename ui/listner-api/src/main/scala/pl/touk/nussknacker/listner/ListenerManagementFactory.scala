package pl.touk.nussknacker.listner

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

trait ListenerManagementFactory {
  def create(config: Config): ListenerManagement
}

object ListenerManagementFactory {
  def serviceLoader(classLoader: ClassLoader): ListenerManagementFactory = {
    aggregate(ScalaServiceLoader.load[ListenerManagementFactory](classLoader): _*)
  }

  def aggregate(factories: ListenerManagementFactory*): ListenerManagementFactory = new ListenerManagementFactory {
    override def create(config: Config): ListenerManagement = {
      val listeners = factories.map(_.create(config))
      new ListenerManagement {
        override def handler(event: ChangeEvent)(implicit ec: ExecutionContext, user: LoggedUser): Unit = {
          listeners.foreach(_.handler(event))
        }
      }
    }
  }
}

