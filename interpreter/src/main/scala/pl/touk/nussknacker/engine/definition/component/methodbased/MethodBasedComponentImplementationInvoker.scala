package pl.touk.nussknacker.engine.definition.component.methodbased

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker

private[definition] class MethodBasedComponentImplementationInvoker(
    obj: Any,
    private[definition] val methodDef: MethodDefinition
) extends ComponentImplementationInvoker
    with LazyLogging {

  override def invokeMethod(
      params: Map[String, Any],
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef]
  ): Any = {
    methodDef.invoke(obj, params, outputVariableNameOpt, additional)
  }

}
