package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
import org.apache.flink.api.scala.typeutils.OptionTypeInfo
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

object ContextType {
  def infoFromVariablesAndParent(variables: TypeInformation[Map[String, Any]], parentCtx: TypeInformation[Option[Context]]): TypeInformation[Context] =
    ConcreteCaseClassTypeInfo (
      ("id", TypeInformation.of(classOf[String])),
      ("variables", variables),
      ("parentContext", parentCtx)
    )

  def infoFromVariablesAndParentOption(variables: TypeInformation[Map[String, Any]], parentOpt: Option[TypeInformation[Context]]): TypeInformation[Context] = {
    val parentCtx = new OptionTypeInfo[Context, Option[Context]](parentOpt.getOrElse(FixedValueTypeInformationHelper.nullValueTypeInfo))
    infoFromVariablesAndParent(variables, parentCtx)
  }

  def infoFromVariables(variables: TypeInformation[Map[String, Any]]): TypeInformation[Context] =
    infoFromVariablesAndParentOption(variables, Some(simpleTypeInformation))

  def infoGeneric: TypeInformation[Context] =
    infoFromVariables(TypeInformation.of(new TypeHint[Map[String, Any]] {}))

  def infoBranch(nodeCtx: FlinkCustomNodeContext, key: String): TypeInformation[Context] =
    nodeCtx.typeInformationDetection.forContext(nodeCtx.validationContext.right.get(key))

  def info(nodeCtx: FlinkCustomNodeContext): TypeInformation[Context] =
    nodeCtx.typeInformationDetection.forContext(nodeCtx.validationContext.left.get)

  private def simpleTypeInformation = TypeInformation.of(classOf[Context])
}
