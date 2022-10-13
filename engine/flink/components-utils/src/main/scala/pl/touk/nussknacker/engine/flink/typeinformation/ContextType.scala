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
    val parentOptionInfo: Option[TypeInformation[Option[Context]]] = parentOpt.map(new OptionTypeInfo(_))
    val parentInfo: TypeInformation[Option[Context]] = parentOptionInfo.getOrElse(FixedValueTypeInformationHelper.nullValueTypeInfo[Option[Context]])
    infoFromVariablesAndParent(variables, parentInfo)
  }

  def infoFromVariables(variables: TypeInformation[Map[String, Any]]): TypeInformation[Context] =
    infoFromVariablesAndParentOption(variables, Some(simpleTypeInformation))

  def infoGeneric: TypeInformation[Context] =
    infoFromVariables(TypeInformation.of(new TypeHint[Map[String, Any]] {}))

  def info(nodeCtx: FlinkCustomNodeContext): TypeInformation[Context] =
    nodeCtx.typeInformationDetection.forContext(nodeCtx.validationContext.left.get)

  private def simpleTypeInformation = TypeInformation.of(classOf[Context])
}
