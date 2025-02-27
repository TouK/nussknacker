package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

import java.util.concurrent.Executor
import scala.concurrent.{ExecutionContext, Future}

object ServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

  override protected val expectedReturnType: Option[Class[_]] = Some(classOf[Future[_]])

  override protected val additionalDependencies = Set[Class[_]](
    classOf[ExecutionContext],
    classOf[ServiceInvocationCollector],
    classOf[MetaData],
    classOf[NodeId],
    classOf[Context],
    classOf[ComponentUseContext]
  )

  override def acceptCustomTransformation: Boolean = false
}

object JavaServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

  override protected val expectedReturnType: Option[Class[_]] = Some(classOf[java.util.concurrent.CompletionStage[_]])

  override protected val additionalDependencies = Set[Class[_]](
    classOf[Executor],
    classOf[ServiceInvocationCollector],
    classOf[MetaData],
    classOf[NodeId],
    classOf[Context],
    classOf[ComponentUseContext]
  )

  override def acceptCustomTransformation: Boolean = false
}

object EagerServiceDefinitionExtractor extends AbstractMethodDefinitionExtractor[Service] {

  override protected val expectedReturnType: Option[Class[_]] = Some(classOf[ServiceInvoker])

  override protected val additionalDependencies = Set[Class[_]](
    classOf[ExecutionContext],
    classOf[ServiceInvocationCollector],
    classOf[MetaData],
    classOf[NodeId],
    classOf[Context],
    classOf[ComponentUseContext]
  )

}
