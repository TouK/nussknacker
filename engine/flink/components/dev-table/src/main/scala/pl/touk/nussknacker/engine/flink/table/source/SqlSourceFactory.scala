package pl.touk.nussknacker.engine.flink.table.source

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.table.extractor.SqlDataSourceConfig

class SqlSourceFactory(configs: List[SqlDataSourceConfig])
    extends SingleInputDynamicComponent[Source]
    with SourceFactory {

  override type State = Nothing

  private val tableNamParamName = "Table"

  private val tableNameParam: ParameterWithExtractor[String] = {
    val param                    = ParameterWithExtractor.mandatory[String](tableNamParamName)
    val possibleTableParamValues = configs.map(c => FixedExpressionValue(s"'${c.tableName}'", c.tableName))
    param.copy(parameter =
      param.parameter.copy(
        editor = Some(FixedValuesParameterEditor(possibleTableParamValues)),
        validators = List(
          MandatoryParameterValidator,
          FixedValuesValidator(possibleTableParamValues)
        )
      )
    )
  }

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        parameters = tableNameParam.parameter :: Nil,
        errors = List.empty,
        state = None
      )
    case TransformationStep((`tableNamParamName`, DefinedEagerParameter(tableName: String, _)) :: Nil, _) =>
      val selectedTable = getSelectedTableUnsafe(tableName)
      val typingResult  = selectedTable.schema.typingResult
      val initializer   = new BasicContextInitializer(typingResult)
      FinalResults.forValidation(context, Nil, None)(initializer.validationContext)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalStateOpt: Option[State]
  ): Source = {
    val tableName     = tableNameParam.extractValue(params)
    val selectedTable = getSelectedTableUnsafe(tableName)
    new SqlSource(selectedTable)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[NodeId])

  // TODO: evaluate based on config
  override val allowedProcessingModes: Option[Set[ProcessingMode]] = Some(Set(ProcessingMode.UnboundedStream))

  private def getSelectedTableUnsafe(tableName: String): SqlDataSourceConfig = {
    configs
      .find(_.tableName == tableName)
      .getOrElse(
        throw new IllegalStateException("Table with selected name not found.")
      )
  }

}
