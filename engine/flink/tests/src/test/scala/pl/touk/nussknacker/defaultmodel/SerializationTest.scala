package pl.touk.nussknacker.defaultmodel

import cats.data.Validated
import cats.data.Validated.valid
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.datastream.DataStream
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MismatchParameter
import pl.touk.nussknacker.engine.api.definition.{
  CustomCompileTimeParameterValidator,
  CustomRuntimeParameterValidator,
  ParameterRuntimeValidationError
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.validation.CustomValidator
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.test.{RunResult, TestScenarioRunner}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage._

class SerializationTest extends AnyFunSuite with Matchers with LazyLogging with BeforeAndAfterAll {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import SerializationTest._

  private lazy val flinkMiniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val testScenarioRunner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
    .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("some serialization test") {
    val scenario = ScenarioBuilder
      .streaming("serialization-test")
      .parallelism(1)
      .source("start", TestScenarioRunner.testDataSource)
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "#input.field2".spel)
    val result =
      testScenarioRunner.runWithData(scenario, List(DataStructureWithOptionals("firstField", Option("optionalField"))))
    result.validValue shouldBe RunResult.success("optionalField")
  }

  // Regression: ClosureCleaner walking FlinkCustomStreamTransformation reached the LazyParameter's
  // CustomParameterValidatorByClassLoader.resolved (non-Serializable validator wrapper) and crashed.
  // Fixed by @transient on resolved in CustomParameterValidatorLoader.
  test(
    "scenario with @CustomValidator(OnlyTrueValidator) on LazyParameter: expression=#input with false input produces runtime validation error"
  ) {
    val runner = TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
      .withExtraComponents(List(ComponentDefinition("filterWithCustomValidator", FilterWithCustomValidatorOnLazyParam)))
      .build()

    val scenario = ScenarioBuilder
      .streaming("custom-validator-serialization-test-false")
      .parallelism(1)
      .source("start", TestScenarioRunner.testDataSource)
      .customNode("filter", "filtered", "filterWithCustomValidator", "expression" -> "#input".spel)
      .emptySink("end", TestScenarioRunner.testResultSink, "value" -> "'ok'".spel)

    val result = runner.runWithData[java.lang.Boolean, String](scenario, List(java.lang.Boolean.FALSE)).validValue
    result.errors should not be empty
    result.successes shouldBe empty
  }

}

private object SerializationTest {
  final case class DataStructureWithOptionals(field1: String, field2: Option[String])

  // CustomStreamTransformer returning FlinkCustomStreamTransformation that captures
  // `expression: LazyParameter[Boolean]` in a lambda — exposing the validator instance to ClosureCleaner
  case object FilterWithCustomValidatorOnLazyParam extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[Void])
    def execute(
        @ParamName("expression") @CustomValidator(classOf[OnlyTrueValidator])
        expression: LazyParameter[
          java.lang.Boolean
        ]
    ): FlinkCustomStreamTransformation =
      FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) =>
        start
          .filter(ctx.lazyParameterHelper.lazyFilterFunction(expression))
          .map((c: Context) => ValueWithContext[AnyRef](null, c), ctx.valueWithContextInfo.forNull[AnyRef])
      )

  }

  // Intentionally not Serializable — the fix must not require validators to implement Serializable
  class OnlyTrueValidator extends CustomRuntimeParameterValidator with CustomCompileTimeParameterValidator {
    override val name: String = "onlyTrue"

    override def isValid(
        paramName: ParameterName,
        expression: Expression,
        value: Option[Any],
        label: Option[String]
    )(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
      value match {
        case Some(true) => valid(())
        case Some(v)    => Validated.invalid(MismatchParameter(s"Value must be true, got: $v", "", paramName, nodeId))
        case None       => valid(())
      }

    override def isValid(paramName: ParameterName, expression: Expression, value: Any)(
        implicit nodeId: NodeId
    ): Validated[ParameterRuntimeValidationError, Unit] =
      if (value == true) valid(())
      else Validated.invalid(ParameterRuntimeValidationError(expression.expression, s"Value must be true, got: $value"))

  }

}
