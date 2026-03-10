package pl.touk.nussknacker.engine.flink.table.io

import org.apache.flink.table.catalog.ObjectIdentifier
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName

object TableComponentFactoryUtils {

  val tableNameParamName: ParameterName = ParameterName("Table")

  def buildTableNameParam(
      defs: List[ObjectIdentifier]
  ): ParameterExtractor[String] with ParameterCreatorWithNoDependency = {
    // TODO: We should use objectName in label only when a table is defined inside the default catalog and the default database
    //       For other cases, we should use qualified paths
    val possibleTableParamValues = defs.map(id => FixedExpressionValue(s"'$id'", id.getObjectName))
    ParameterDeclaration
      .mandatory[String](tableNameParamName)
      .withCreator(
        modify = _.copy(
          editors = List(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: possibleTableParamValues)),
          changesCanReloadParameters = Some(true)
        )
      )
  }

  // There is no easy way to create ObjectIdentifier from String and we don't want to add this type to Nu supported types so we pass table id as String instead
  def getSelectedTableIdUnsafe(
      tableIdString: String,
      configs: List[ObjectIdentifier]
  ): ObjectIdentifier =
    configs
      .find(_.toString == tableIdString)
      .getOrElse(throw new IllegalStateException("Table with selected name not found."))

}
