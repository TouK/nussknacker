package pl.touk.nussknacker.ui.api.helpers

import java.sql.DriverManager

import com.whisk.docker.{DockerCommandExecutor, DockerContainer, DockerContainerState, DockerKit, DockerReadyChecker}

import scala.concurrent.ExecutionContext
import scala.util.Try

trait PostgresContainer extends DockerKit {
  import scala.concurrent.duration._
  def PostgresAdvertisedPort = 5432
  def PostgresExposedPort = 15432
  val PostgresUser = "postgres"
  val PostgresPassword = "postgres"

  val postgresContainer: DockerContainer = DockerContainer("postgres:11.2")
    .withPorts((PostgresAdvertisedPort, Some(PostgresExposedPort)))
    .withEnv(s"POSTGRES_USER=$PostgresUser", s"POSTGRES_PASSWORD=$PostgresPassword")
    .withReadyChecker(
      new PostgresReadyChecker(PostgresUser, PostgresPassword, Some(PostgresExposedPort))
        .looped(15, 1.second)
    )
  
  abstract override def dockerContainers: List[DockerContainer] =
    postgresContainer :: super.dockerContainers
}

class PostgresReadyChecker(user: String, password: String, port: Option[Int] = None)
  extends DockerReadyChecker {

  override def apply(container: DockerContainerState)(implicit docker: DockerCommandExecutor,
                                                      ec: ExecutionContext) =
    container
      .getPorts()
      .map(ports =>
        Try {
          Class.forName("org.postgresql.Driver")
          val url = s"jdbc:postgresql://${docker.host}:${port.getOrElse(ports.values.head)}/"
          Option(DriverManager.getConnection(url, user, password)).map(_.close).isDefined
        }.getOrElse(false))
}