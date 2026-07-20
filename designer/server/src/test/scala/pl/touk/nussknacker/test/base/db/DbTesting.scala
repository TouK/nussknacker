package pl.touk.nussknacker.test.base.db

import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.scalatest.time.{Second, Seconds, Span}
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.engine.api.db.DbRef
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.db.DbRefInstance

import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

trait WithTestDb extends BeforeAndAfterAll {
  this: Suite =>

  def testDbConfig: Config

  private lazy val (dbRef, releaseDbRefResources) = DbRefInstance.create(testDbConfig).allocated.unsafeRunSync()

  def testDbRef: DbRef = dbRef

  override protected def afterAll(): Unit = {
    releaseDbRefResources.unsafeRunSync()
    super.afterAll()
  }

  protected def getSchemaName(): String = testDbConfig.getString("db.schema")
}

trait WithTestHsqlDb extends WithTestDb {
  self: Suite =>

  override val testDbConfig: Config = ConfigFactory.parseMap(
    Map(
      "db" -> Map(
        "user"     -> "SA",
        "password" -> "",
        "url"      -> "jdbc:hsqldb:mem:esp;sql.syntax_ora=true",
        "driver"   -> "org.hsqldb.jdbc.JDBCDriver",
        "schema"   -> "testschema"
      ).asJava
    ).asJava
  )

}

trait WithTestPostgresDb extends WithTestDb {
  self: Suite with ForAllTestContainer =>

  override val container: PostgreSQLContainer =
    PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))

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
    session.prepareStatement(s"""delete from "${getSchemaName()}"."process_attachments"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."process_actions"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."process_comments"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."scenario_activities"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."process_versions"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."scenario_labels"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."environments"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."processes"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."fingerprints"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."scheduled_scenarios"""").execute()
    session.prepareStatement(s"""delete from "${getSchemaName()}"."scheduled_scenario_deployments"""").execute()
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

  override def cleanDB(): Try[Unit] = {
    val superResult = super.cleanDB()
    val locksResult = Using(testDbRef.db.createSession()) { session =>
      session.prepareStatement(s"""delete from "${getSchemaName()}"."distributed_locks"""").execute()
    }.map(_ => ())
    superResult.flatMap(_ => locksResult)
  }

}
