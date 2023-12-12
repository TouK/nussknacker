package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.ScenarioProcessingContext
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo
import pl.touk.nussknacker.engine.flink.typeinformation.{ConcreteCaseClassTypeInfo, FixedValueTypeInformationHelper}

object ContextTypeHelpers {

  private def infoFromVariablesAndParent(
      variables: TypeInformation[Map[String, Any]],
      parentCtx: TypeInformation[Option[ScenarioProcessingContext]]
  ): TypeInformation[ScenarioProcessingContext] =
    ConcreteCaseClassTypeInfo(
      ("id", TypeInformation.of(classOf[String])),
      ("variables", variables),
      ("parentContext", parentCtx)
    )

  def infoFromVariablesAndParentOption(
      variables: TypeInformation[Map[String, Any]],
      parentOpt: Option[TypeInformation[ScenarioProcessingContext]]
  ): TypeInformation[ScenarioProcessingContext] = {
    val parentCtx = new OptionTypeInfo[ScenarioProcessingContext, Option[ScenarioProcessingContext]](
      parentOpt.getOrElse(FixedValueTypeInformationHelper.nullValueTypeInfo)
    )
    infoFromVariablesAndParent(variables, parentCtx)
  }

}
