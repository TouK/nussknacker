package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Method

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed

import scala.reflect.ClassTag

class ProcessObjectDefinitionExtractor[F, T: ClassTag] extends AbstractMethodDefinitionExtractor[F] {

  override protected def expectedReturnType: Option[Class[_]] = Some(implicitly[ClassTag[T]].runtimeClass)
  override protected def additionalDependencies: Set[Class[_]] = Set[Class[_]](classOf[MetaData], classOf[NodeId])

}

class SourceProcessObjectDefinitionExtractor extends ProcessObjectDefinitionExtractor[SourceFactory[_], Source[Any]] {

  override def extractReturnTypeFromMethod(sourceFactory: SourceFactory[_], method: Method): typing.TypingResult = Typed(sourceFactory.clazz)
}

object SignalsDefinitionExtractor extends AbstractMethodDefinitionExtractor[ProcessSignalSender] {

  // could expect void but because of often skipping return type declaration in methods and type inference, would be to rigorous
  override protected val expectedReturnType: Option[Class[_]] = None
  override protected val additionalDependencies: Set[Class[_]] = Set[Class[_]](classOf[String])

}

object ProcessObjectDefinitionExtractor {

  val source = new SourceProcessObjectDefinitionExtractor
  val sink = new ProcessObjectDefinitionExtractor[SinkFactory, Sink]
  val exceptionHandler = new ProcessObjectDefinitionExtractor[ExceptionHandlerFactory, EspExceptionHandler]
  val customNodeExecutor: CustomStreamTransformerExtractor.type = CustomStreamTransformerExtractor
  val service: MethodDefinitionExtractor[Service] = ServiceInvoker.Extractor
  val signals: SignalsDefinitionExtractor.type = SignalsDefinitionExtractor

}
