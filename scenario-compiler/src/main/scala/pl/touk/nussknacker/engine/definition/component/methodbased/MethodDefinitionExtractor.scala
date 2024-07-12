package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory, Source, SourceFactory}

import java.lang.reflect.Method

// We should think about things that happens here as a Dependency Injection where @ParamName and so on are kind of
// BindingAnnotation in guice meaning. Maybe we should switch to some lightweight DI framework (like guice) instead
// of writing its on ours own?
private[component] trait MethodDefinitionExtractor[T] {

  def extractMethodDefinition(
      obj: T,
      methodToInvoke: Method,
      parametersConfig: Map[ParameterName, ParameterConfig]
  ): Either[String, MethodDefinition]

}

object MethodDefinitionExtractor {

  val Source                  = new ComponentMethodDefinitionExtractor[SourceFactory, Source]
  val Sink                    = new ComponentMethodDefinitionExtractor[SinkFactory, Sink]
  val CustomStreamTransformer = CustomStreamTransformerExtractor

  val Service = new UnionDefinitionExtractor[Service](
    ServiceDefinitionExtractor ::
      JavaServiceDefinitionExtractor ::
      EagerServiceDefinitionExtractor ::
      Nil
  )

}
