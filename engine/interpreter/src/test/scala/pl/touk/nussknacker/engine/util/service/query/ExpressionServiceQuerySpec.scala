package pl.touk.nussknacker.engine.util.service.query

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.engine.util.service.query.QueryServiceTesting.CreateQuery
import pl.touk.nussknacker.engine.util.service.query.ServiceQuerySpec.ConcatService

class ExpressionServiceQuerySpec extends FlatSpec with Matchers with ScalaFutures {
  import pl.touk.nussknacker.engine.spel.Implicits._

  override def spanScaleFactor: Double = 2
  import scala.concurrent.ExecutionContext.Implicits.global
  val modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator())

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

  private def invokeConcatService(s1: String, s2: String) =
    invokeService(new ConcatService, "s1" -> s1, "s2" -> s2)

  private def invokeService(service: Service, args: (String, Expression)*) = {
    ExpressionServiceQuery(CreateQuery("srv", service), modelData)
      .invoke("srv", args: _*)
  }

}
