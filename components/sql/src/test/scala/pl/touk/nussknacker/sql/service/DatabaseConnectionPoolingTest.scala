package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.hsqldb.jdbc.JDBCDriver
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
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
    with BeforeAndAfterEach
    with WithHsqlDB
    with Matchers
    with OptionValues {

  private def createDatabaseComponents() = DatabaseEnricherComponentProvider.create(
    ConfigFactory.parseString(
      s"""config {
         |  databaseQueryEnricher {
         |     name: query-enricher
         |     dbPool {
         |       driverClassName: "${classOf[ConnectionCountingDriver].getName}",
         |       username: "$username",
         |       password: "$password",
         |       url: "$url"
         |       # initialSize = maxTotal means that connections will be utilized
         |       initialSize: 1
         |       maxTotal: 1
         |       minimumIdle: 10000
         |     }
         |  }
         |}
         |""".stripMargin
    )
  )

  override def prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE people (id INT, name VARCHAR(40));",
    "INSERT INTO people (id, name) VALUES (1, 'John');",
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    ConnectionCountingDriver.clearInvocationsCount()
  }

  test("DatabaseQueryEnricher should use connection pooling during scenario validation and run") {
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

    val scenarioRunner = TestScenarioRunner
      .liteBased()
      .withExtraComponents(createDatabaseComponents())
      .build()
    val result = scenarioRunner
      .runWithData[String, String](scenario, List("foo"))
      .validValue

    result.errors shouldBe empty
    result.successes.loneElement shouldBe "John"
    ConnectionCountingDriver.connectInvocationsCount.get() shouldBe <=(2)
    // expected count is <= 2 because we have one pool used during node compilation and the second one during scenario runtime
  }

  test("DatabaseQueryEnricher should use connection pooling during node compilation") {
    val nodeCompiler = TestNodeCompiler
      .liteBased()
      .withExtraComponents(createDatabaseComponents())
      .build()
    def compileEnricherNode() = {
      nodeCompiler.compileNode(
        Enricher(
          NodeId("query-enricher"),
          NodeName("query-enricher"),
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
    checkNodeCompilationResult(compilationResult)

    ConnectionCountingDriver.connectInvocationsCount.get() shouldBe 1
    compileEnricherNode()
    ConnectionCountingDriver.connectInvocationsCount.get() shouldBe 1
  }

  private def checkNodeCompilationResult(
      compilationResult: NodeCompiler.NodeCompilationResult[NodeCompiler.EnricherCompilationResult]
  ) = {
    compilationResult.compiledObject shouldBe Symbol("valid")
    val outputType = compilationResult.validationContext.validValue.get("output").value
    outputType shouldBe Typed.record(
      List(
        "ID"   -> Typed[Int],
        "NAME" -> Typed[String],
      )
    )
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
