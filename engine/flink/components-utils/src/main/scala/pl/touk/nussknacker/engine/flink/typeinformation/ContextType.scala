package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
import org.apache.flink.api.scala.typeutils.OptionTypeInfo
import pl.touk.nussknacker.engine.api.Context

object ContextType {
  def info(variables: TypeInformation[Map[String, Any]], parentCtx: Option[TypeInformation[Context]]): TypeInformation[Context] =
    ConcreteCaseClassTypeInfo (
      ("id", TypeInformation.of(classOf[String])),
      ("variables", variables),
      ("parentContext", parentCtx.map(new OptionTypeInfo(_)).getOrElse(FixedValueTypeInformationHelper.nullValueTypeInfo))
    )

  def info(variables: TypeInformation[Map[String, Any]]): TypeInformation[Context] =
    info(variables, Some(simpleTypeInformation))

  def info: TypeInformation[Context] =
    info(TypeInformation.of(new TypeHint[Map[String, Any]] {}))

  private def simpleTypeInformation = TypeInformation.of(classOf[Context])
}
