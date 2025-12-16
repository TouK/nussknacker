package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.hsqldb.jdbc.JDBCDriver
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node.Enricher
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.lite.util.test.LiteNodeCompiler.LiteNodeCompilerExt
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.{TestNodeCompiler, TestScenarioRunner}
import pl.touk.nussknacker.sql.DatabaseEnricherComponentProvider
import pl.touk.nussknacker.sql.service.DatabaseConnectionPoolingTest.ConnectionCountingDriver
import pl.touk.nussknacker.sql.service.DatabaseConnectionPoolingTest.ConnectionCountingDriver.connectInvocationsCount
import pl.touk.nussknacker.sql.utils.WithHsqlDB
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.sql.Connection
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger

class DatabaseConnectionPoolingTest
    extends AnyFunSuite
    with BeforeAndAfterAll
    with WithHsqlDB
    with Matchers
    with OptionValues {

  private val databaseComponents = DatabaseEnricherComponentProvider.create(
    ConfigFactory.parseString(
      s"""config {
         |  databaseQueryEnricher {
         |     name: query-enricher
         |     dbPool {
         |       driverClassName: "${classOf[ConnectionCountingDriver].getName}",
         |       username: "$username",
         |       password: "$password",
         |       url: "$url"
         |     }
         |  }
         |}
         |""".stripMargin
    )
  )

  private val nodeCompiler = TestNodeCompiler
    .liteBased()
    .withExtraComponents(databaseComponents)
    .build()

  private val scenarioRunner = TestScenarioRunner
    .liteBased()
    .withExtraComponents(databaseComponents)
    .build()

  override def prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE people (id INT, name VARCHAR(40));",
    "INSERT INTO people (id, name) VALUES (1, 'John');",
  )

  test("DatabaseQueryEnricher should use connection pooling during metadata discovery") {
    ConnectionCountingDriver.clearInvocationsCount()

    def compileEnricherNode() = {
      nodeCompiler.compileNode(
        Enricher(
          "query-enricher",
          ServiceRef(
            "query-enricher",
            List(
              Parameter(ParameterName("Query"), "select * from people where id = ?".spelTemplate),
              Parameter(ParameterName("arg1"), "1".spel),
            )
          ),
          "output"
        )
      )
    }

    val compilationResult = compileEnricherNode()
    compilationResult.compiledObject shouldBe Symbol("valid")
    val outputType = compilationResult.validationContext.validValue.get("output").value
    outputType shouldBe Typed.record(
      List(
        "ID"   -> Typed[Int],
        "NAME" -> Typed[String],
      )
    )
    ConnectionCountingDriver.connectInvocationsCount.get() shouldBe 1
    compileEnricherNode()
    ConnectionCountingDriver.connectInvocationsCount.get() shouldBe 1

    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", TestScenarioRunner.testDataSource)
      .enricher(
        "query-enricher",
        "output",
        "query-enricher",
        "Query" -> "select * from people where id = ?".spelTemplate,
        "arg1"  -> "1".spel
      )
      .emptySink("sink", TestScenarioRunner.testResultSink, "value" -> "#output.NAME".spel)

    val result = scenarioRunner
      .runWithData[String, String](scenario, List("foo"))
      .validValue

    result.errors shouldBe empty
    result.successes.loneElement shouldBe "John"
  }

}

object DatabaseConnectionPoolingTest {

  object ConnectionCountingDriver {

    def clearInvocationsCount(): Unit = {
      connectInvocationsCount.set(0)
    }

    val connectInvocationsCount = new AtomicInteger(0)

  }

  class ConnectionCountingDriver extends JDBCDriver {

    override def connect(url: String, info: Properties): Connection = {
      connectInvocationsCount.incrementAndGet()
      super.connect(url, info)
    }

  }

}
