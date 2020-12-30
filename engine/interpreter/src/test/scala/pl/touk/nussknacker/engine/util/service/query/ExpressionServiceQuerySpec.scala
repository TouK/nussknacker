package pl.touk.nussknacker.engine.util.service.query

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.util.service.query.QueryServiceTesting.CreateQuery
import pl.touk.nussknacker.engine.util.service.query.ServiceQuerySpec.ConcatService
import pl.touk.nussknacker.test.PatientScalaFutures

class ExpressionServiceQuerySpec extends FlatSpec with Matchers with PatientScalaFutures {
  import pl.touk.nussknacker.engine.spel.Implicits._

  override def spanScaleFactor: Double = 2
  import scala.concurrent.ExecutionContext.Implicits.global
  val modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator() {
    override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig =
      super.expressionConfig(processObjectDependencies).copy(globalProcessVariables = Map("GLOBAL" -> WithCategories("globalValue")))
  })

  it should "evaluate spel expressions" in {
    whenReady(invokeConcatService("'foo'", "'bar'")) { r =>
      r.result shouldBe "foobar"
    }
  }

  it should "evaluate spel expressions with math expression" in {
    whenReady(invokeConcatService("'foo'", "(1 + 2).toString()")) { r =>
      r.result shouldBe "foo3"
    }
  }

  it should "allow using global variables" in {
    whenReady(invokeConcatService("'foo'", "#GLOBAL")) { r =>
      r.result shouldBe "fooglobalValue"
    }
  }

  private def invokeConcatService(s1: String, s2: String) =
    invokeService(new ConcatService, "s1" -> s1, "s2" -> s2)

  private def invokeService(service: Service, args: (String, Expression)*) = {
    ExpressionServiceQuery(CreateQuery("srv", service), modelData)
      .invoke("srv", args: _*)
  }

}
