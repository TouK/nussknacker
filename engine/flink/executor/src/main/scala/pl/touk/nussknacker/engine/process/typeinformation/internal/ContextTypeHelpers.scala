package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo
import pl.touk.nussknacker.engine.flink.typeinformation.{ConcreteCaseClassTypeInfo, FixedValueTypeInformationHelper}

object ContextTypeHelpers {

  private def infoFromVariablesAndParent(variables: TypeInformation[Map[String, Any]],
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

}
