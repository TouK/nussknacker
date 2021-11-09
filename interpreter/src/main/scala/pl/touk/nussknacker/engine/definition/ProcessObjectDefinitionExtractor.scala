package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender

import scala.reflect.ClassTag

class ProcessObjectDefinitionExtractor[F, T: ClassTag] extends AbstractMethodDefinitionExtractor[F] {

  override protected def expectedReturnType: Option[Class[_]] = Some(implicitly[ClassTag[T]].runtimeClass)
  override protected def additionalDependencies: Set[Class[_]] = Set[Class[_]](classOf[MetaData], classOf[NodeId], classOf[RunMode])

}

object SourceProcessObjectDefinitionExtractor extends ProcessObjectDefinitionExtractor[SourceFactory[_], Source[Any]]

object SignalsDefinitionExtractor extends AbstractMethodDefinitionExtractor[ProcessSignalSender] {

  // could expect void but because of often skipping return type declaration in methods and type inference, would be to rigorous
  override protected val expectedReturnType: Option[Class[_]] = None
  override protected val additionalDependencies: Set[Class[_]] = Set[Class[_]](classOf[String])

}

object ProcessObjectDefinitionExtractor {

  val source = SourceProcessObjectDefinitionExtractor
  val sink = new ProcessObjectDefinitionExtractor[SinkFactory, Sink]
  val customNodeExecutor: CustomStreamTransformerExtractor.type = CustomStreamTransformerExtractor
  val service: MethodDefinitionExtractor[Service] = DefaultServiceInvoker.Extractor
  val signals: SignalsDefinitionExtractor.type = SignalsDefinitionExtractor
}
