package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.api.context.transformation.{
  DynamicComponent,
  OutputVariableNameValue,
  TypedNodeDependencyValue
}
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.definition.component.ComponentRuntimeLogicFactory

class DynamicComponentRuntimeLogicFactory(obj: DynamicComponent[_]) extends ComponentRuntimeLogicFactory {

  override def createRuntimeLogic(
      params: Params,
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef]
  ): Any = {
    val additionalParams = obj.nodeDependencies.map {
      case TypedNodeDependency(klazz) =>
        additional
          .find(klazz.isInstance)
          .map(TypedNodeDependencyValue)
          .getOrElse(throw new IllegalArgumentException(s"Failed to find dependency: $klazz"))
      case OutputVariableNameDependency =>
        outputVariableNameOpt
          .map(OutputVariableNameValue)
          .getOrElse(throw new IllegalArgumentException("Output variable not defined"))
      case other => throw new IllegalArgumentException(s"Cannot handle dependency $other")
    }
    val finalStateValue = additional
      .collectFirst { case FinalStateValue(value) => value }
      .getOrElse(throw new IllegalArgumentException("Final state not passed to invokeMethod"))
    // we assume parameters were already validated!
    obj.createRuntimeLogic(params, additionalParams, finalStateValue.asInstanceOf[Option[obj.State]])
  }

}

case class FinalStateValue(value: Option[Any])
