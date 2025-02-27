package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._

import scala.reflect.ClassTag

class ComponentMethodDefinitionExtractor[F, T: ClassTag] extends AbstractMethodDefinitionExtractor[F] {

  override protected def expectedReturnType: Option[Class[_]] = Some(implicitly[ClassTag[T]].runtimeClass)
  override protected def additionalDependencies: Set[Class[_]] =
    Set[Class[_]](classOf[MetaData], classOf[NodeId], classOf[ComponentUseContext])

}
