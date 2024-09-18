package pl.touk.nussknacker.test.base.db

import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.time.{Second, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.db.{DatabaseInitializer, DbRef}

import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

trait WithTestDb extends BeforeAndAfterAll {
  this: Suite =>

  def testDbConfig: Config

  private lazy val (dbRef, releaseDbRefResources) = DbRef.create(testDbConfig).allocated.unsafeRunSync()

  def testDbRef: DbRef = dbRef

  override protected def afterAll(): Unit = {
    releaseDbRefResources.unsafeRunSync()
    super.afterAll()
  }

}

trait WithTestHsqlDb extends WithTestDb {
  self: Suite =>

  override val testDbConfig: Config = ConfigFactory.parseMap(
    Map(
      "db" -> Map(
        "user"     -> "SA",
        "password" -> "",
        "url"      -> "jdbc:hsqldb:mem:esp;sql.syntax_ora=true",
        "driver"   -> "org.hsqldb.jdbc.JDBCDriver"
      ).asJava
    ).asJava
  )

}

trait WithTestPostgresDb extends WithTestDb {
  self: Suite with ForAllTestContainer =>

  override val container: PostgreSQLContainer =
    PostgreSQLContainer(DockerImageName.parse("postgres:11.2"))

  override def testDbConfig: Config = ConfigFactory.parseMap(
    Map(
      "db" -> Map(
        "user"     -> container.username,
        "password" -> container.password,
        "url"      -> container.jdbcUrl,
        "driver"   -> "org.postgresql.Driver",
        "schema"   -> "testschema"
      ).asJava
    ).asJava
  )

}

trait DbTesting extends BeforeAndAfterEach with BeforeAndAfterAll {
  self: Suite with WithTestDb =>

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    DatabaseInitializer.initDatabase("db", testDbConfig)
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    cleanDB().failed.foreach { e =>
      throw new InternalError(
        "Error during cleaning test resources",
        e
      ) // InternalError as scalatest swallows other exceptions in afterEach
    }
  }

  def cleanDB(): Try[Unit] = Using(testDbRef.db.createSession()) { session =>
    session.prepareStatement("""delete from "process_attachments"""").execute()
    session.prepareStatement("""delete from "process_comments"""").execute()
    session.prepareStatement("""delete from "process_actions"""").execute()
    session.prepareStatement("""delete from "process_versions"""").execute()
    session.prepareStatement("""delete from "scenario_labels"""").execute()
    session.prepareStatement("""delete from "environments"""").execute()
    session.prepareStatement("""delete from "processes"""").execute()
    session.prepareStatement("""delete from "fingerprints"""").execute()
  }

}

trait WithHsqlDbTesting extends DbTesting with WithTestHsqlDb {
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
