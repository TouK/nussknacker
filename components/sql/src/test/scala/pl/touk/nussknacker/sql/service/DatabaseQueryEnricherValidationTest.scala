package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, InvalidDurationParameter}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.definition.PositiveDurationValidation
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node.Enricher
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.lite.util.test.LiteNodeCompiler.LiteNodeCompilerExt
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestNodeCompiler
import pl.touk.nussknacker.sql.DbEnricherName
import pl.touk.nussknacker.sql.db.query.{ResultSetStrategy, SingleResultStrategy}
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.time.Duration

class DatabaseQueryEnricherValidationTest extends BaseHsqlQueryEnricherTest {

  override val service =
    new DatabaseQueryEnricher(hsqlDbPoolConfig, DbEnricherName("queryEnricher"))

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')"
  )

  test("should handle query parsing failure gracefully") {
    implicit val nodeId: NodeId = NodeId("test")
    val vCtx                    = ValidationContext(Map("some" -> Typed[String]))
    val outVarName              = "out"

    val result = service
      .contextTransformation(vCtx, List(OutputVariableNameValue(outVarName)))
      .apply(
        service.TransformationStep(
          List(
            DatabaseQueryEnricher.resultStrategyParamName -> eagerValueParameter(SingleResultStrategy.name),
            DatabaseQueryEnricher.queryParamName          -> eagerValueParameter("select from"),
            DatabaseQueryEnricher.cacheTTLParamName       -> eagerValueParameter(Duration.ofMinutes(1)),
          ),
          None
        )
      )

    val expectedOutputContext = vCtx
      .withVariable(OutputVar.customNode(outVarName), Unknown)
      .getOrElse(throw new AssertionError("Should not happen"))

    result shouldBe service.FinalResults(
      expectedOutputContext,
      List(
        CustomNodeError("unexpected token: FROM in statement [select from]", Some(DatabaseQueryEnricher.queryParamName))
      )
    )
  }

  test("should handle non-parametrized queries") {
    implicit val nodeId: NodeId = NodeId("test")
    val vCtx                    = ValidationContext(Map("some" -> Typed[String]))
    val outVarName              = "out"

    val result = service
      .contextTransformation(vCtx, List(OutputVariableNameValue(outVarName)))
      .apply(
        service.TransformationStep(
          List(
            DatabaseQueryEnricher.resultStrategyParamName -> eagerValueParameter(ResultSetStrategy.name),
            DatabaseQueryEnricher.queryParamName          -> eagerValueParameter("select * from persons"),
            DatabaseQueryEnricher.cacheTTLParamName       -> eagerValueParameter(Duration.ofMinutes(1)),
          ),
          None
        )
      )

    result match {
      case service.FinalResults(expectedOutputContext, _, _) => expectedOutputContext.contains("out") shouldBe true
      case _                                                 => fail("Enricher does not return final results")
    }
  }

  test("should reject a zero Cache TTL during scenario compilation") {
    val nodeCompiler = TestNodeCompiler
      .liteBased()
      .withExtraComponents(List(ComponentDefinition(name = "queryEnricher", component = service)))
      .build()

    val nodeId = NodeId("query-enricher")

    val compilationResult = nodeCompiler.compileNode(
      Enricher(
        nodeId,
        NodeName("query-enricher"),
        ServiceRef(
          "queryEnricher",
          List(
            Parameter(DatabaseQueryEnricher.resultStrategyParamName, s"'${SingleResultStrategy.name}'".spel),
            Parameter(DatabaseQueryEnricher.queryParamName, "select * from persons".spelTemplate),
            Parameter(DatabaseQueryEnricher.cacheTTLParamName, "T(java.time.Duration).parse('PT0S')".spel),
          )
        ),
        "out"
      )
    )

    compilationResult.compiledObject.invalidValue.toList should contain(
      InvalidDurationParameter(
        PositiveDurationValidation.Message,
        PositiveDurationValidation.Description,
        DatabaseQueryEnricher.cacheTTLParamName,
        nodeId
      )
    )
  }

  private def eagerValueParameter(value: Any) = DefinedEagerParameter(value, null)

}
