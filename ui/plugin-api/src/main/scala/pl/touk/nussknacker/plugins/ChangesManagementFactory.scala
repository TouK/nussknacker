package pl.touk.nussknacker.plugins

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.restmodel.process.repository.{FetchingProcessRepository, ProcessActivityRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

final case class ChangesManagementDependencies(processRepository: FetchingProcessRepository,
                                               activityRepository: ProcessActivityRepository)

trait ChangesManagementFactory {
  def create(config: Config, dependencies: ChangesManagementDependencies): ChangesManagement
}

object ChangesManagementFactory {
  def serviceLoader(classLoader: ClassLoader): ChangesManagementFactory = {
    aggregate(ScalaServiceLoader.load[ChangesManagementFactory](classLoader): _*)
  }

  def aggregate(factories: ChangesManagementFactory*): ChangesManagementFactory = new ChangesManagementFactory {
    override def create(config: Config, dependencies: ChangesManagementDependencies): ChangesManagement = {
      val changes = factories.map(_.create(config, dependencies))
      new ChangesManagement {
        override def handler(event: ChangeEvent)(implicit ec: ExecutionContext, user: LoggedUser): Unit = {
          changes.foreach(_.handler(event))
        }
      }
    }
  }
}

