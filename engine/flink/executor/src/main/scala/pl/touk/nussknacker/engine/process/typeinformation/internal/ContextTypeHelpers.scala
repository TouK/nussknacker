package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.{ListTypeInfo, PojoField, PojoTypeInfo}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.flink.typeinformation.{ConcreteCaseClassTypeInfo, FixedValueTypeInformationHelper}

import scala.jdk.CollectionConverters._

object ContextTypeHelpers {

  private def infoFromVariablesAndParent(
      variables: TypeInformation[java.util.Map[String, Any]],
      parentCtx: TypeInformation[_ <: Context]
  ): TypeInformation[Context] =
    new PojoTypeInfo(
      classOf[Context],
      List(
        new PojoField(classOf[Context].getDeclaredField("id"), contextIdInfo),
        new PojoField(classOf[Context].getDeclaredField("javaMapVariables"), variables),
        new PojoField(classOf[Context].getDeclaredField("nullableParentContext"), parentCtx),
        new PojoField(classOf[Context].getDeclaredField("nullableTraceId"), traceIdTypeInfo),
      ).asJava
    )

  def infoFromVariablesAndParentOption(
      variables: TypeInformation[java.util.Map[String, Any]],
      parentOpt: Option[TypeInformation[Context]]
  ): TypeInformation[Context] = {
    val parentCtx = parentOpt.getOrElse(FixedValueTypeInformationHelper.nullValueTypeInfo)
    infoFromVariablesAndParent(variables, parentCtx)
  }

  private val processNameTypeInformation: TypeInformation[ProcessName] =
    ConcreteCaseClassTypeInfo[ProcessName](("value", TypeInformation.of(classOf[String])))

  private val nodeIdTypeInformation: TypeInformation[NodeId] =
    ConcreteCaseClassTypeInfo[NodeId](("id", TypeInformation.of(classOf[String])))

  private val contextIdPathPartInfo: TypeInformation[ContextIdPathPart] = {
    ConcreteCaseClassTypeInfo(
      ("nodeId", nodeIdTypeInformation),
      ("value", TypeInformation.of(classOf[String])),
    )
  }

  private val contextIdInfo: TypeInformation[ContextId] = {
    ConcreteCaseClassTypeInfo(
      ("scenarioName", processNameTypeInformation),
      ("originatingNodeId", nodeIdTypeInformation),
      ("taskId", TypeInformation.of(classOf[Long])),
      ("index", TypeInformation.of(classOf[Long])),
      ("contextIdPath", new ListTypeInfo[ContextIdPathPart](contextIdPathPartInfo)),
    )
  }

  private val traceIdTypeInfo: TypeInformation[TraceId] =
    ConcreteCaseClassTypeInfo(("value", TypeInformation.of(classOf[String])))

}
