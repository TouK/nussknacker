package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData

class CronEditorTest extends AnyFunSuite with Matchers {

  private val modelData: LocalModelData =
    LocalModelData(ConfigFactory.load(), List.empty, configCreator = new DevProcessConfigCreator)

  private val compiler = ExpressionCompiler.withoutOptimization(modelData)

  test("parses cron expression") {
    val expression =
      "new com.cronutils.parser.CronParser(T(com.cronutils.model.definition.CronDefinitionBuilder).instanceDefinitionFor(T(com.cronutils.model.CronType).QUARTZ)).parse('0 0 00 1/1 * ? *')"
    val result = compiler.compile(Expression.spel(expression), None, ValidationContext.empty, Unknown)(NodeId(""))
    result shouldBe Symbol("valid")
  }

}
