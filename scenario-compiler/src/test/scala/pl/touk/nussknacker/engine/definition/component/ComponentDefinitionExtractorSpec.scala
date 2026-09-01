package pl.touk.nussknacker.engine.definition.component

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import eu.timepit.refined.auto._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{
  BranchParamName,
  CustomStreamTransformer,
  LazyParameter,
  MethodToInvoke,
  NodeId,
  OutputVariableName,
  ParamName
}
import pl.touk.nussknacker.engine.api.component.ComponentOutput
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ContextTransformation.DummyStreamTransformerImplementation
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionExtractorSpec._

// An empty or whitespace-only output name needs no test: `ComponentOutput("")` does not compile.
class ComponentDefinitionExtractorSpec extends AnyFunSuite with Matchers {

  test("fails when a custom transformer declares duplicate output names") {
    val exception = intercept[IllegalArgumentException] {
      ComponentDefinitionWithImplementation.withEmptyConfig("duplicateOutputs", DuplicateOutputsTransformer)
    }
    exception.getMessage should include("duplicateOutputs")
    exception.getMessage should include("rejected")
  }

  test("fails when a joining custom transformer declares additional outputs") {
    val exception = intercept[IllegalArgumentException] {
      ComponentDefinitionWithImplementation.withEmptyConfig("joinWithOutputs", JoinTransformerWithOutputs)
    }
    exception.getMessage should include("joinWithOutputs")
  }

  test("fails when an additional output duplicates the declared main output's name") {
    val exception = intercept[IllegalArgumentException] {
      ComponentDefinitionWithImplementation.withEmptyConfig("mainDuplicated", MainNameDuplicatedTransformer)
    }
    exception.getMessage should include("mainDuplicated")
    exception.getMessage should include("passed")
  }

}

object ComponentDefinitionExtractorSpec {

  object DuplicateOutputsTransformer extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("stringVal") stringVal: LazyParameter[String]) = DummyStreamTransformerImplementation

    override def outputs: NonEmptyList[ComponentOutput] =
      NonEmptyList.of(ComponentOutput.MainOutput, ComponentOutput.RejectedOutput, ComponentOutput.RejectedOutput)

  }

  object JoinTransformerWithOutputs extends CustomStreamTransformer {

    @MethodToInvoke
    def execute(
        @BranchParamName("value") valueByBranchId: Map[String, LazyParameter[_]],
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId): JoinContextTransformation = {
      ContextTransformation.join
        .definedBy(_ => Valid(ValidationContext(Map(variableName -> Typed[String]))))
        .notImplemented[DummyStreamTransformerImplementation]
    }

    override def outputs: NonEmptyList[ComponentOutput] =
      NonEmptyList.of(ComponentOutput.MainOutput, ComponentOutput.RejectedOutput)

  }

  object MainNameDuplicatedTransformer extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute() = DummyStreamTransformerImplementation

    override def outputs: NonEmptyList[ComponentOutput] =
      NonEmptyList.of(ComponentOutput("passed"), ComponentOutput("passed"))
  }

}
