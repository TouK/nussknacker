package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.implinvoker.ComponentImplementationInvoker
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, Source}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import shapeless.syntax.typeable._

abstract class StubbedFlinkProcessCompiler(
    process: CanonicalProcess,
    creator: ProcessConfigCreator,
    modelConfig: Config,
    diskStateBackendSupport: Boolean,
    objectNaming: ObjectNaming,
    componentUseCase: ComponentUseCase
) extends FlinkProcessCompiler(
      creator,
      modelConfig,
      diskStateBackendSupport,
      objectNaming,
      componentUseCase,
    ) {

  import pl.touk.nussknacker.engine.util.Implicits._

  override protected def definitions(
      processObjectDependencies: ProcessObjectDependencies,
      userCodeClassLoader: ClassLoader
  ): (ModelDefinitionWithClasses, EngineDictRegistry) = {
    val (originalDefinitionWithTypes, originalDictRegistry) =
      super.definitions(processObjectDependencies, userCodeClassLoader)
    val originalDefinition = originalDefinitionWithTypes.modelDefinition

    val collectedSources = process.allStartNodes.map(_.head.data).collect { case source: Source =>
      source
    }

    lazy val context =
      ComponentDefinitionContext(userCodeClassLoader, originalDefinitionWithTypes, originalDictRegistry)
    val usedSourceTypes = collectedSources.map(_.ref.typ)
    val stubbedSources =
      usedSourceTypes.map { sourceType =>
        val sourceDefinition = originalDefinition.sourceFactories.getOrElse(
          sourceType,
          throw new IllegalArgumentException(s"Source $sourceType cannot be stubbed - missing definition")
        )
        val stubbedDefinition = prepareSourceFactory(sourceDefinition, context)
        sourceType -> stubbedDefinition
      }

    def sourceDefForFragment(frag: FragmentInputDefinition): ComponentDefinitionWithImplementation = {
      new StubbedFragmentInputDefinitionSource(
        LocalModelData(modelConfig, creator, modelClassLoader = ModelClassLoader(userCodeClassLoader, List()))
      ).createSourceDefinition(frag)
    }

    val stubbedSourceForFragment: Seq[(String, ComponentDefinitionWithImplementation)] =
      process.allStartNodes.map(_.head.data).collect { case frag: FragmentInputDefinition =>
        frag.id -> prepareSourceFactory(sourceDefForFragment(frag), context)
      }

    val stubbedServices = originalDefinition.services.mapValuesNow(prepareService(_, context))

    (
      ModelDefinitionWithClasses(
        originalDefinition
          .copy(
            sourceFactories = originalDefinition.sourceFactories ++ stubbedSources ++ stubbedSourceForFragment,
            services = stubbedServices
          )
      ),
      originalDictRegistry
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

case class ComponentDefinitionContext(
    userCodeClassLoader: ClassLoader,
    originalDefinitionWithTypes: ModelDefinitionWithClasses,
    originalDictRegistry: EngineDictRegistry
)

abstract class StubbedComponentImplementationInvoker(
    original: ComponentImplementationInvoker,
    originalReturnType: Option[TypingResult]
) extends ComponentImplementationInvoker {
  def this(componentDefinitionWithImpl: ComponentDefinitionWithImplementation) =
    this(componentDefinitionWithImpl.implementationInvoker, componentDefinitionWithImpl.returnType)

  override def invokeMethod(
      params: Map[String, Any],
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef]
  ): Any = {
    def transform(impl: Any): Any = {
      val typingResult = impl.cast[ReturningType].map(rt => Some(rt.returnType)).getOrElse(originalReturnType)
      val nodeId = additional
        .collectFirst { case nodeId: NodeId =>
          nodeId
        }
        .getOrElse(throw new IllegalArgumentException("Node id is missing in additional parameters"))

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
