package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker
}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, Source}
import pl.touk.nussknacker.engine.process.compiler.StubbedComponentImplementationInvoker.returnType
import shapeless.syntax.typeable.typeableOps

abstract class StubbedFlinkProcessCompilerDataFactory(
    process: CanonicalProcess,
    creator: ProcessConfigCreator,
    extractModelDefinition: ExtractDefinitionFun,
    modelConfig: Config,
    objectNaming: ObjectNaming,
    componentUseCase: ComponentUseCase
) extends FlinkProcessCompilerDataFactory(
      creator,
      extractModelDefinition,
      modelConfig,
      objectNaming,
      componentUseCase,
    ) {

  import pl.touk.nussknacker.engine.util.Implicits._

  override protected def adjustDefinitions(
      originalModelDefinition: ModelDefinition,
      definitionContext: ComponentDefinitionContext
  ): ModelDefinition = {
    val collectedSources = process.allStartNodes.map(_.head.data).collect { case source: Source =>
      source
    }
    val usedSourceTypes = collectedSources.map(_.ref.typ)
    val stubbedSources =
      usedSourceTypes.map { sourceName =>
        val sourceDefinition = originalModelDefinition
          .getComponent(ComponentType.Source, sourceName)
          .getOrElse(
            throw new IllegalArgumentException(s"Source $sourceName cannot be stubbed - missing definition")
          )
        val stubbedDefinition = prepareSourceFactory(sourceDefinition, definitionContext)
        ComponentId(ComponentType.Source, sourceName) -> stubbedDefinition
      }

    val fragmentParametersDefinitionExtractor = new FragmentParametersWithoutValidatorsDefinitionExtractor(
      definitionContext.userCodeClassLoader
    )
    val fragmentSourceDefinitionPreparer = new StubbedFragmentSourceDefinitionPreparer(
      fragmentParametersDefinitionExtractor
    )

    val stubbedSourceForFragments =
      process.allStartNodes.map(_.head.data).collect { case frag: FragmentInputDefinition =>
        val fragmentSourceDefWithImpl = fragmentSourceDefinitionPreparer.createSourceDefinition(frag)
        ComponentId(ComponentType.Fragment, frag.id) -> prepareSourceFactory(
          fragmentSourceDefWithImpl,
          definitionContext
        )
      }

    val stubbedServices = originalModelDefinition.components
      .filter(_._1.`type` == ComponentType.Service)
      .mapValuesNow(prepareService(_, definitionContext))

    originalModelDefinition
      .copy(
        components =
          originalModelDefinition.components ++ stubbedSources ++ stubbedSourceForFragments ++ stubbedServices
      )
  }

  protected def prepareService(
      service: ComponentDefinitionWithImplementation,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithImplementation

  protected def prepareSourceFactory(
      sourceFactory: ComponentDefinitionWithImplementation,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithImplementation

}

abstract class StubbedComponentImplementationInvoker(
    original: ComponentImplementationInvoker,
    originalDefinitionReturnType: Option[TypingResult]
) extends ComponentImplementationInvoker {

  def this(componentDefinitionWithImpl: ComponentDefinitionWithImplementation) = {
    this(
      componentDefinitionWithImpl.implementationInvoker,
      returnType(componentDefinitionWithImpl)
    )
  }

  override def invokeMethod(
      params: Map[String, Any],
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef]
  ): Any = {
    def transform(impl: Any): Any = {
      // Correct TypingResult is important for method based components, because even for testing and verification
      // purpose, ImplementationInvoker is used also to determine output types. Dynamic components don't use it during
      // scenario validation so we pass Unknown for them
      val typingResult =
        impl
          .cast[ReturningType]
          .map(rt => rt.returnType)
          .orElse(originalDefinitionReturnType)
          .getOrElse(Unknown)
      val nodeId = additional
        .collectFirst { case nodeId: NodeId =>
          nodeId
        }
        .getOrElse(throw new IllegalArgumentException("Node id is missing in additional parameters"))

      handleInvoke(impl, typingResult, nodeId)
    }

    val originalValue = original.invokeMethod(params, outputVariableNameOpt, additional)
    originalValue match {
      case contextTransformation: ContextTransformation =>
        contextTransformation.copy(implementation = transform(contextTransformation.implementation))
      case componentExecutor => transform(componentExecutor)
    }
  }

  def handleInvoke(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any
}

object StubbedComponentImplementationInvoker {

  private def returnType(componentDefinitionWithImpl: ComponentDefinitionWithImplementation): Option[TypingResult] = {
    componentDefinitionWithImpl match {
      case methodBasedDefinition: MethodBasedComponentDefinitionWithImplementation => methodBasedDefinition.returnType
      case _: DynamicComponentDefinitionWithImplementation                         => None
    }
  }

}
