package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo
import pl.touk.nussknacker.engine.flink.typeinformation.{ConcreteCaseClassTypeInfo, FixedValueTypeInformationHelper}

object ContextTypeHelpers {
  def infoFromVariablesAndParent(variables: TypeInformation[Map[String, Any]],
                                 parentCtx: TypeInformation[Option[Context]]): TypeInformation[Context] =
    ConcreteCaseClassTypeInfo (
      ("id", TypeInformation.of(classOf[String])),
      ("variables", variables),
      ("parentContext", parentCtx)
    )

  def infoFromVariablesAndParentOption(variables: TypeInformation[Map[String, Any]],
                                       parentOpt: Option[TypeInformation[Context]]): TypeInformation[Context] = {
    val parentCtx = new OptionTypeInfo[Context, Option[Context]](parentOpt.getOrElse(FixedValueTypeInformationHelper.nullValueTypeInfo))
    infoFromVariablesAndParent(variables, parentCtx)
  }

  def infoFromVariables(variables: TypeInformation[Map[String, Any]]): TypeInformation[Context] =
    infoFromVariablesAndParentOption(variables, Some(simpleTypeInformation))

  def infoGeneric: TypeInformation[Context] =
    infoFromVariables(TypeInformation.of(new TypeHint[Map[String, Any]] {}))

  private def simpleTypeInformation = TypeInformation.of(classOf[Context])
}
