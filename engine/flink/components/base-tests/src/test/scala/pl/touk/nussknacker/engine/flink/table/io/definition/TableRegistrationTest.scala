package pl.touk.nussknacker.engine.flink.table.io.definition

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.scalatest.{BeforeAndAfterAll, LoneElement, Outcome}
import org.scalatest.Inside.inside
import org.scalatest.funspec.FixtureAnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinitionRegistrationError.{
  CatalogRegistrationError,
  TableRegistrationError
}
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

class TableRegistrationTest
    extends FixtureAnyFunSpec
    with Matchers
    with LoneElement
    with ValidatedValuesDetailedMessage
    with TableDrivenPropertyChecks
    with PatientScalaFutures
    with BeforeAndAfterAll {

  override protected type FixtureParam = StreamExecutionEnvironment

  private val miniClusterWithServices =
    FlinkMiniClusterFactory
      .createUnitTestsMiniClusterWithServices()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    // Ensure the PostgreSQL JDBC driver is loaded
    Class.forName("org.postgresql.Driver")
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    miniClusterWithServices.close()
  }

  override protected def withFixture(test: OneArgTest): Outcome = {
    miniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      test(env)
    }
  }

  private def registerTables(
      sql: String,
      sEnv: StreamExecutionEnvironment
  ) = {
    val parsedSql      = FlinkDdlParser.parseUnsafe(sql)
    val dataDefinition = FlinkDataDefinition.applyUnsafe(parsedSql, None)
    val env            = StreamTableEnvironment.create(sEnv)
    dataDefinition.registerIn(env)
  }

  it("returns error for table under non-default database") { sEnv =>
    val sql =
      """|CREATE TABLE somedb.testTable
         |(
         |    someString  STRING
         |) WITH (
         |    'connector' = 'datagen'
         |);""".stripMargin
    val registrationError = registerTables(sql, sEnv).invalidValue.toList.loneElement
    inside(registrationError) { case e: TableRegistrationError =>
      e.message should include("Cause: Database somedb does not exist in Catalog default_catalog")
    }
  }

  it("should return error if cannot connect to catalog at discovery preparation") { sEnv =>
    val sql =
      """|CREATE CATALOG my_catalog WITH (
         |    'type' = 'jdbc',
         |    'default-database' = 'default-db',
         |    'username' = 'username',
         |    'password' = 'password',
         |    'base-url' = 'jdbc:postgresql://localhost:5432'
         |)""".stripMargin
    val registrationError = registerTables(sql, sEnv).invalidValue.toList.loneElement
    inside(registrationError) { case e: CatalogRegistrationError =>
      e.message shouldBe
        """Could not create catalog.
          |Cause: Connection refused""".stripMargin
    }
  }

  it("should return error if catalog cannot be found on classpath") { sEnv =>
    val sql =
      """|CREATE CATALOG test_catalog WITH (
         |    'type' = 'non-existant-catalog'
         |)""".stripMargin
    val registrationError = registerTables(sql, sEnv).invalidValue.toList.loneElement
    inside(registrationError) { case e: CatalogRegistrationError =>
      e.message shouldBe
        """Could not create catalog.
          |Cause: Could not find any factory for identifier 'non-existant-catalog' that implements 'org.apache.flink.table.factories.CatalogFactory' in the classpath.
          |
          |Available factory identifiers are:
          |
          |generic_in_memory
          |jdbc
          |stubbed""".stripMargin
    }
  }

}
