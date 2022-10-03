package pl.touk.nussknacker.ui.api.helpers

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DbConfig}
import slick.jdbc.{HsqldbProfile, JdbcBackend, JdbcProfile, PostgresProfile}

import scala.collection.convert.DecorateAsJava
import scala.util.{Try, Using}

trait DbTesting
  extends BeforeAndAfterEach
    with BeforeAndAfterAll
    with LazyLogging
    with DecorateAsJava {
  self: Suite =>

  val dbProfile: JdbcProfile
  val config: Config

  lazy val db: DbConfig = DbConfig(JdbcBackend.Database.forConfig("db", config), dbProfile)

  override def beforeAll(): Unit = {
    super.beforeAll()
    DatabaseInitializer.initDatabase("db", config)
  }

  override protected def afterEach(): Unit = {
    cleanDB().failed.foreach { e =>
      throw new InternalError("Error during cleaning test resources", e) //InternalError as scalatest swallows other exceptions in afterEach
    }
  }

  def cleanDB(): Try[Unit] = Using(db.db.createSession()) { session =>
    session.prepareStatement("""delete from "process_attachments"""").execute()
    session.prepareStatement("""delete from "process_comments"""").execute()
    session.prepareStatement("""delete from "process_actions"""").execute()
    session.prepareStatement("""delete from "process_versions"""").execute()
    session.prepareStatement("""delete from "tags"""").execute()
    session.prepareStatement("""delete from "environments"""").execute()
    session.prepareStatement("""delete from "processes"""").execute()
  }
}

trait WithHsqlDbTesting
  extends DbTesting {
  self: Suite =>

  override val dbProfile = HsqldbProfile
  override lazy val config: Config = ConfigFactory.parseMap(Map(
    "db" -> Map(
      "user" -> "SA",
      "password" -> "",
      "url" -> "jdbc:hsqldb:mem:esp;sql.syntax_ora=true",
      "driver" -> "org.hsqldb.jdbc.JDBCDriver"
    ).asJava).asJava)
}

trait WithPostgresDbTesting
  extends PatientScalaFutures
    with ForAllTestContainer
    with DbTesting {
  self: Suite =>

  override lazy val config: Config = ConfigFactory.parseMap(Map(
    "db" -> Map(
      "user" -> container.username,
      "password" -> container.password,
      "url" -> container.jdbcUrl,
      "driver" -> "org.postgresql.Driver",
      "schema" -> "testschema"
    ).asJava).asJava)
  override val dbProfile = PostgresProfile

  implicit val pc: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))

  override val container: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:11.2"))


}
