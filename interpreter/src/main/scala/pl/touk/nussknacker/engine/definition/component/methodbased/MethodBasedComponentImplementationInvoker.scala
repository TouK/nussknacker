package pl.touk.nussknacker.engine.definition.component.methodbased

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker

private[definition] class MethodBasedComponentImplementationInvoker(
    obj: Any,
    private[definition] val methodDef: MethodDefinition
) extends ComponentImplementationInvoker
    with LazyLogging {

  override def invokeMethod(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
    methodDef.invoke(obj, params.nameToValueMap, outputVariableNameOpt, additional)
  }

}
