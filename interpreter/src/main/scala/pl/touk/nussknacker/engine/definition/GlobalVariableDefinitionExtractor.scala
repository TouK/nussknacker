package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.definition.TypedNodeDependency
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef, StandardObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.{MethodDefinition, OrderedDependencies}

object GlobalVariableDefinitionExtractor {

  def extractDefinitions(objs: Map[String, WithCategories[AnyRef]]): Map[String, ObjectWithMethodDef] = {
    objs.map { case (varName, varWithCategories) =>
      (varName, extractDefinition(varName, varWithCategories))
    }
  }

  private def extractDefinition(varName: String, varWithCategories: WithCategories[AnyRef]): StandardObjectWithMethodDef = {
    val returnType = varWithCategories.value match {
      case typedGlobalVariable: TypedGlobalVariable => typedGlobalVariable.initialReturnType
      case obj => Typed.fromInstance(obj)
    }
    val methodDef = MethodDefinition(
      name = varName,
      invocation = (obj, deps) => obj match {
        case typedGlobalVariable: TypedGlobalVariable => typedGlobalVariable.value(deps.head.asInstanceOf[MetaData])
        case _ => obj
      },
      orderedDependencies = new OrderedDependencies(List(TypedNodeDependency(classOf[MetaData]))),
      returnType = returnType,
      // Used only for services.
      runtimeClass = classOf[Any],
      annotations = Nil
    )
    val objectDef = ObjectDefinition(
      parameters = Nil,
      returnType = returnType,
      categories = varWithCategories.categories,
      nodeConfig = varWithCategories.nodeConfig
    )
    StandardObjectWithMethodDef(varWithCategories.value, methodDef, objectDef)
  }
}
