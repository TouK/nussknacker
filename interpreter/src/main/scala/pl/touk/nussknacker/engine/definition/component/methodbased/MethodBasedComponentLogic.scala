package pl.touk.nussknacker.engine.definition.component.methodbased

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.definition.component.ComponentLogic

private[definition] class MethodBasedComponentLogic(
    obj: Any,
    private[definition] val methodDef: MethodDefinition
) extends ComponentLogic
    with LazyLogging {

  override def run(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
    methodDef.invoke(obj, params.nameToValueMap, outputVariableNameOpt, additional)
  }

}
