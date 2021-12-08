package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.definition.OutputVariableNameDependency
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.sql.db.query.SingleResultStrategy
import pl.touk.nussknacker.sql.db.schema.MetaDataProviderFactory
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest

import java.time.Duration

class DatabaseQueryEnricherValidationTest extends BaseHsqlQueryEnricherTest {

  override val service = new DatabaseQueryEnricher(hsqlDbPoolConfig, new MetaDataProviderFactory().create(hsqlDbPoolConfig))

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  test("should handle query parsing failure gracefully") {
    implicit val nodeId: NodeId = NodeId("test")
    val vCtx = ValidationContext(Map("some" -> Typed[String]))
    val outVarName = "out"

    val result = service.contextTransformation(vCtx, List(OutputVariableNameValue(outVarName))).apply(
      service.TransformationStep(List(
        DatabaseQueryEnricher.ResultStrategyParam.name-> eagerValueParameter(SingleResultStrategy.name),
        DatabaseQueryEnricher.QueryParamName -> eagerValueParameter("select from"),
        DatabaseQueryEnricher.CacheTTLParam.name -> eagerValueParameter(Duration.ofMinutes(1)),
      ), None)
    )
    
    val expectedOutputContext = vCtx.withVariable(OutputVar.customNode(outVarName), Unknown)
      .getOrElse(throw new AssertionError("Should not happen"))

    result shouldBe service.FinalResults(
      expectedOutputContext,
      List(CustomNodeError("unexpected token: FROM in statement [select from]", Some(DatabaseQueryEnricher.QueryParamName)))
    )
  }

  private def eagerValueParameter(value: Any) = DefinedEagerParameter(value, null)

}
