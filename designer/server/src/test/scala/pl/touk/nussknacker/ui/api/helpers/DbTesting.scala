package pl.touk.nussknacker.ui.api.helpers

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DbRef}

import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

trait WithTestDb extends BeforeAndAfterAll {
  this: Suite =>

  val config: Config

  private val (dbRef, releaseDbRefResources) = DbRef.create(config).allocated.unsafeRunSync()

  def testDbRef: DbRef = dbRef

  abstract override def afterAll(): Unit = {
    releaseDbRefResources.unsafeRunSync()
    super.afterAll()
  }
}

trait WithTestHsqlDb extends WithTestDb {
  self: Suite =>

  override lazy val config: Config = ConfigFactory.parseMap(Map(
    "db" -> Map(
      "user" -> "SA",
      "password" -> "",
      "url" -> "jdbc:hsqldb:mem:esp;sql.syntax_ora=true",
      "driver" -> "org.hsqldb.jdbc.JDBCDriver"
    ).asJava).asJava)
}

trait WithTestPostgresDb extends WithTestDb {
  self: Suite with ForAllTestContainer =>

  override lazy val config: Config = ConfigFactory.parseMap(Map(
    "db" -> Map(
      "user" -> container.username,
      "password" -> container.password,
      "url" -> container.jdbcUrl,
      "driver" -> "org.postgresql.Driver",
      "schema" -> "testschema"
    ).asJava).asJava)

  override val container: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:11.2"))

}

trait DbTesting
  extends BeforeAndAfterEach
    with BeforeAndAfterAll
    with LazyLogging {
  self: Suite with WithTestDb =>

  override def beforeAll(): Unit = {
    super.beforeAll()
    DatabaseInitializer.initDatabase("db", config)
  }

  override protected def afterEach(): Unit = {
    cleanDB().failed.foreach { e =>
      throw new InternalError("Error during cleaning test resources", e) //InternalError as scalatest swallows other exceptions in afterEach
    }
  }

  def cleanDB(): Try[Unit] = Using(testDbRef.db.createSession()) { session =>
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
  extends DbTesting
    with WithTestHsqlDb {
  self: Suite =>
}

trait WithPostgresDbTesting
  extends DbTesting
    with PatientScalaFutures
    with ForAllTestContainer
    with WithTestPostgresDb {
  self: Suite =>

  implicit val pc: PatienceConfig = PatienceConfig(Span(20, Seconds), Span(1, Second))

}
