package pl.touk.nussknacker.engine.definition.component.implinvoker

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodDefinition

trait ComponentImplementationInvoker extends Serializable {

  def invokeMethod(params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any

}

object ComponentImplementationInvoker {

  val nullImplementationInvoker: ComponentImplementationInvoker = new ComponentImplementationInvoker {

    override def invokeMethod(
        params: Map[String, Any],
        outputVariableNameOpt: Option[String],
        additional: Seq[AnyRef]
    ): Any = null

  }

}

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
