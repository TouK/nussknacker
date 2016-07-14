package pl.touk.process.model.graph

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.process.model.api.Ctx
import pl.touk.process.model.graph.expression.SpelExpression
import scala.collection.JavaConverters._

import scala.beans.BeanProperty

class ExpressionSpec extends FlatSpec with Matchers {

  val testValue = Test( "1", 2, List(Test("3", 4), Test("5", 6)).asJava)
  val ctx = Ctx(Map(), Map("obj" -> testValue), Map())

  case class Test(@BeanProperty id: String, @BeanProperty value: Long, @BeanProperty children: java.util.List[Test] = List[Test]().asJava)

  it should "invoke simple expression" in {

    SpelExpression("#obj.value + 4").evaluate[Long](ctx) should equal(6)

  }

  it should "filter by list predicates" in {

    SpelExpression("#obj.children.?[id == '55'].empty").evaluate[Boolean](ctx) should equal(true)
    SpelExpression("#obj.children.?[id == '5'].size()").evaluate[Integer](ctx) should equal(1: Integer)

  }


}
