package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ComponentImplementationInvoker, ObjectDefinition, ObjectWithMethodDef, StandardObjectWithMethodDef}

object GlobalVariableDefinitionExtractor {

  import pl.touk.nussknacker.engine.util.Implicits._

  def extractDefinitions(objs: Map[String, WithCategories[AnyRef]]): Map[String, ObjectWithMethodDef] = {
    objs.mapValuesNow(extractDefinition)
  }

  def extractDefinition(varWithCategories: WithCategories[AnyRef]): StandardObjectWithMethodDef = {
    val returnType = varWithCategories.value match {
      case typedGlobalVariable: TypedGlobalVariable => typedGlobalVariable.initialReturnType
      case obj => Typed.fromInstance(obj)
    }
    val objectDef = ObjectDefinition(
      parameters = Nil,
      returnType = Some(returnType),
      categories = varWithCategories.categories,
      componentConfig = varWithCategories.componentConfig
    )
    StandardObjectWithMethodDef(
      // Global variables are always accessed by StandardObjectWithMethodDef.obj - see GlobalVariablesPreparer
      // and comment in ObjectWithMethodDef.implementationInvoker
      ComponentImplementationInvoker.nullImplementationInvoker,
      varWithCategories.value,
      objectDef,
      // Used only for services
      classOf[Any])
  }

}
