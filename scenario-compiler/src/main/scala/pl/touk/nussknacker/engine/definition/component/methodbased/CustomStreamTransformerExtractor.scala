package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MetaData, NodeId, NodeName}
import pl.touk.nussknacker.engine.api.process.ComponentUseContext

object CustomStreamTransformerExtractor extends AbstractMethodDefinitionExtractor[CustomStreamTransformer] {

  override protected val expectedReturnType: Option[Class[_]] = None

  override protected val additionalDependencies: Set[Class[_]] =
    Set[Class[_]](classOf[NodeId], classOf[NodeName], classOf[MetaData], classOf[ComponentUseContext])

}
