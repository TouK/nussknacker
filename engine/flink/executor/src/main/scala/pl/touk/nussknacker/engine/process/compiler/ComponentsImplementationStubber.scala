package pl.touk.nussknacker.engine.process.compiler

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ComponentImplementationInvoker, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ModelDefinitionWithTypes
import pl.touk.nussknacker.engine.graph.node.Source
import shapeless.syntax.typeable._

abstract class ComponentsImplementationStubber(process: CanonicalProcess) extends Serializable {

  import pl.touk.nussknacker.engine.util.Implicits._

  def stubImplementations(modelDefinitionWithTypes: ModelDefinitionWithTypes): ModelDefinitionWithTypes = {
    import modelDefinitionWithTypes.modelDefinition
    val collectedSources = process.allStartNodes.map(_.head.data).collect {
      case source: Source => source
    }

    val usedSourceTypes = collectedSources.map(_.ref.typ)
    val stubbedSources =
      usedSourceTypes.map { sourceType =>
        val sourceDefinition = modelDefinition.sourceFactories.getOrElse(sourceType, throw new IllegalArgumentException(s"Source $sourceType cannot be stubbed - missing definition"))
        val stubbedDefinition = prepareSourceFactory(sourceDefinition)
        sourceType -> stubbedDefinition
      }

    val stubbedServices = modelDefinition.services.mapValuesNow(prepareService)

    modelDefinitionWithTypes.copy(modelDefinition
      .copy(
        sourceFactories = modelDefinition.sourceFactories ++ stubbedSources,
        services = stubbedServices))
  }

  protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef

  protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef

}

abstract class StubbedComponentImplementationInvoker(original: ComponentImplementationInvoker,
                                                     originalReturnType: Option[TypingResult]) extends ComponentImplementationInvoker {

  def this(componentDefinitionWithImpl: ObjectWithMethodDef) =
    this(componentDefinitionWithImpl.implementationInvoker, componentDefinitionWithImpl.returnType)

  override def invokeMethod(params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
    def transform(impl: Any): Any = {
      val typingResult = impl.cast[ReturningType].map(rt => Some(rt.returnType)).getOrElse(originalReturnType)
      val nodeId = additional.collectFirst {
        case nodeId: NodeId => nodeId
      }.getOrElse(throw new IllegalArgumentException("Node id is missing in additional parameters"))

      handleInvoke(impl, typingResult, nodeId)
    }

    val originalValue = original.invokeMethod(params, outputVariableNameOpt, additional)
    originalValue match {
      case e: ContextTransformation =>
        e.copy(implementation = transform(e.implementation))
      case e => transform(e)
    }
  }

  def handleInvoke(impl: Any, typingResult: Option[TypingResult], nodeId: NodeId): Any

}
