package pl.touk.nussknacker.engine.definition.component.methodbased

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.definition.component.ComponentLogic

private[definition] class MethodBasedComponentLogic(
    obj: Any,
    private[definition] val methodDef: MethodDefinition
) extends ComponentLogic
    with LazyLogging {

  override def run(
      params: Map[String, Any],
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef]
  ): Any = {
    methodDef.invoke(obj, params, outputVariableNameOpt, additional)
  }

}
