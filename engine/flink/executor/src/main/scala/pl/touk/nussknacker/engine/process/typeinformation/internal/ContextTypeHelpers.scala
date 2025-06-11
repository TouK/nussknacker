package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ListTypeInfo
import pl.touk.nussknacker.engine.api.{Context, ContextId, ContextIdPathPart}
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo
import pl.touk.nussknacker.engine.flink.typeinformation.{ConcreteCaseClassTypeInfo, FixedValueTypeInformationHelper}

object ContextTypeHelpers {

  private def infoFromVariablesAndParent(
      variables: TypeInformation[Map[String, Any]],
      parentCtx: TypeInformation[Option[Context]]
  ): TypeInformation[Context] =
    ConcreteCaseClassTypeInfo(
      ("id", contextIdInfo),
      ("variables", variables),
      ("parentContext", parentCtx)
    )

  def infoFromVariablesAndParentOption(
      variables: TypeInformation[Map[String, Any]],
      parentOpt: Option[TypeInformation[Context]]
  ): TypeInformation[Context] = {
    val parentCtx = new OptionTypeInfo[Context, Option[Context]](
      parentOpt.getOrElse(FixedValueTypeInformationHelper.nullValueTypeInfo)
    )
    infoFromVariablesAndParent(variables, parentCtx)
  }

  private def contextIdInfo: TypeInformation[ContextId] = {
    ConcreteCaseClassTypeInfo(
      ("scenarioId", TypeInformation.of(classOf[String])),
      ("originatingNodeId", TypeInformation.of(classOf[String])),
      ("taskId", TypeInformation.of(classOf[Long])),
      ("index", TypeInformation.of(classOf[Long])),
      ("contextIdPath", new ListTypeInfo[ContextIdPathPart](contextIdTransformationInfo)),
    )
  }

  private def contextIdTransformationInfo: TypeInformation[ContextIdPathPart] = {
    ConcreteCaseClassTypeInfo(
      ("nodeId", TypeInformation.of(classOf[String])),
      ("value", TypeInformation.of(classOf[String])),
    )
  }

}
