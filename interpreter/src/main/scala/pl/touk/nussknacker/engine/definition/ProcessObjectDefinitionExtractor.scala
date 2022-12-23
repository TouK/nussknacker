package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.NodeId

import scala.reflect.ClassTag

class ProcessObjectDefinitionExtractor[F, T: ClassTag] extends AbstractMethodDefinitionExtractor[F] {

  override protected def expectedReturnType: Option[Class[_]] = Some(implicitly[ClassTag[T]].runtimeClass)
  override protected def additionalDependencies: Set[Class[_]] = Set[Class[_]](classOf[MetaData], classOf[NodeId], classOf[ComponentUseCase])

}

object SourceProcessObjectDefinitionExtractor extends ProcessObjectDefinitionExtractor[SourceFactory, Source]

object ProcessObjectDefinitionExtractor {

  val source = SourceProcessObjectDefinitionExtractor
  val sink = new ProcessObjectDefinitionExtractor[SinkFactory, Sink]
  val customStreamTransformer: CustomStreamTransformerExtractor.type = CustomStreamTransformerExtractor
  val service: MethodDefinitionExtractor[Service] = DefaultServiceInvoker.Extractor

}
