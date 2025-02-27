package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MetaData, NodeId}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase

object CustomStreamTransformerExtractor extends AbstractMethodDefinitionExtractor[CustomStreamTransformer] {

  override protected val expectedReturnType: Option[Class[_]] = None

  override protected val additionalDependencies: Set[Class[_]] =
    Set[Class[_]](classOf[NodeId], classOf[MetaData], classOf[ComponentUseCase])

}
