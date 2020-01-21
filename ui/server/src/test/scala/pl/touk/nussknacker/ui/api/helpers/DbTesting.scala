package pl.touk.nussknacker.ui.api.helpers

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.typesafe.scalalogging.LazyLogging
import com.whisk.docker.DockerFactory
import com.whisk.docker.impl.spotify.SpotifyDockerFactory
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DbConfig}
import slick.jdbc.{HsqldbProfile, JdbcBackend, PostgresProfile}
import slick.util.AsyncExecutor

import scala.util.Try

trait DbTesting
  extends BeforeAndAfterEach
    with BeforeAndAfterAll
    with LazyLogging {
  self: Suite =>

  val db: DbConfig
  
  override protected def afterEach(): Unit = {
    cleanDB().failed.foreach { e =>
      throw new InternalError("Error during cleaning test resources", e) //InternalError as scalatest swallows other exceptions in afterEach
    }
  }

  def cleanDB(): Try[Unit] = {
    Try {
      val session = db.db.createSession()
      session.prepareStatement("""delete from "process_attachments"""").execute()
      session.prepareStatement("""delete from "process_comments"""").execute()
      session.prepareStatement("""delete from "process_actions"""").execute()
      session.prepareStatement("""delete from "process_versions"""").execute()
      session.prepareStatement("""delete from "tags"""").execute()
      session.prepareStatement("""delete from "environments"""").execute()
      session.prepareStatement("""delete from "processes"""").execute()
    }
  }
}

trait WithHsqlDbTesting
  extends DbTesting {
  self: Suite =>

  override def beforeAll(): Unit = {
    super.beforeAll()
    new DatabaseInitializer(db).initDatabase()
  }

  val db: DbConfig = DbConfig(JdbcBackend.Database.forURL(
    url = s"jdbc:hsqldb:mem:esp;sql.syntax_ora=true",
    driver = "org.hsqldb.jdbc.JDBCDriver",
    user = "SA",
    password = "",
    //we don't use default because it uses too much connections and causes warning, fixed only in: https://github.com/slick/slick/commit/318843a1b81817d800ba14c9c749b6f2045b340c
    executor = AsyncExecutor.default("Nussknacker-test", 20)
  ), HsqldbProfile)
}

trait WithPostgresDbTesting
  extends PostgresContainer
    with PatientScalaFutures
    with DockerTestKit
    with DbTesting {
  self: Suite =>

  override def beforeAll(): Unit = {
    super.beforeAll()
    new DatabaseInitializer(db).initDatabase()
  }

  implicit val pc: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))

  private val client: DockerClient = DefaultDockerClient.fromEnv().build()

  override implicit val dockerFactory: DockerFactory = new SpotifyDockerFactory(client)

  lazy val db: DbConfig = DbConfig(JdbcBackend.Database.forURL(
    url = s"jdbc:postgresql://${dockerExecutor.host}:15432/",
    driver = "org.postgresql.Driver",
    user = "postgres",
    password = "postgres",
    executor = AsyncExecutor.default("Nussknacker-test", 20)
  ), PostgresProfile)
}
