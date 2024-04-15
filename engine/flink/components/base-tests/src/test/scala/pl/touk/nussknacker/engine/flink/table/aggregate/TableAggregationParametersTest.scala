package pl.touk.nussknacker.engine.flink.table.aggregate

import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{NodeId, VariableConstants}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  DefinedLazyParameter,
  OutputVariableNameValue
}
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class TableAggregationParametersTest extends AnyFunSuite with Matchers {

  implicit val nodeId: NodeId = NodeId("id")
  val aggregationComponent    = new TableAggregationFactory()

  // TODO: after adding more aggregators do a table based test for these cases here
  test("should return errors for mismatch between aggregate function and aggregated-by value") {
    val definition =
      aggregationComponent.contextTransformation(ValidationContext(), List(OutputVariableNameValue("out")))

    val result = definition(
      aggregationComponent.TransformationStep(
        parameters = List(
          TableAggregationFactory.groupByParamName            -> DefinedLazyParameter(Typed[String]),
          TableAggregationFactory.aggregateByParamName        -> DefinedLazyParameter(Typed[String]),
          TableAggregationFactory.aggregatorFunctionParamName -> DefinedEagerParameter("Sum", Typed[String]),
        ),
        state = None
      )
    )
    inside(result.errors) { case CustomNodeError(_, _, Some(TableAggregationFactory.aggregateByParamName)) :: Nil =>
    }
  }

  // TODO: after adding more aggregators do a table based test for these cases here
  test("should return correct types for valid parameters") {
    val outVariableName = "out"
    val definition =
      aggregationComponent.contextTransformation(ValidationContext(), List(OutputVariableNameValue(outVariableName)))

    val result = definition(
      aggregationComponent.TransformationStep(
        parameters = List(
          TableAggregationFactory.groupByParamName            -> DefinedLazyParameter(Typed[String]),
          TableAggregationFactory.aggregateByParamName        -> DefinedLazyParameter(Typed[Int]),
          TableAggregationFactory.aggregatorFunctionParamName -> DefinedEagerParameter("Sum", Typed[String]),
        ),
        state = None
      )
    )

    inside(result) { case aggregationComponent.FinalResults(finalContext, Nil, _) =>
      finalContext shouldBe ValidationContext(
        Map(
          outVariableName                   -> Typed[Int],
          VariableConstants.KeyVariableName -> Typed[String]
        )
      )
    }

  }

}
