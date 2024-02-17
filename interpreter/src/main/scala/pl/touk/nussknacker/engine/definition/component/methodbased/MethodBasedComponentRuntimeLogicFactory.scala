package pl.touk.nussknacker.engine.definition.component.methodbased

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.definition.component.ComponentRuntimeLogicFactory

private[definition] class MethodBasedComponentRuntimeLogicFactory(
    obj: Any,
    private[definition] val methodDef: MethodDefinition
) extends ComponentRuntimeLogicFactory
    with LazyLogging {

  override def createRuntimeLogic(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
    methodDef.invoke(obj, params.nameToValueMap, outputVariableNameOpt, additional)
  }

}
