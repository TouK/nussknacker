package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.component.Component

/**
  * Hook for using Apache Flink API directly.
  * See examples in pl.touk.nussknacker.engine.example.custom
  *
  * IMPORTANT lifecycle notice:
  * Implementations of this class *must not* allocate resources (connections, file handles etc.)
**/
//TODO this could be scala-trait, but we leave it as abstract class for now for java compatibility
//We should consider separate interfaces for java implementation, but right now we convert ProcessConfigCreator
//from java to scala one and is seems difficult to convert java CustomStreamTransformer, Service etc. into scala ones
abstract class CustomStreamTransformer extends Component {

  /**
    * deprecated - use ContextTransformation.join instead
    */
  // TODO: remove after full switch to ContextTransformation API
  def canHaveManyInputs: Boolean = false

  // For now it is only supported by Flink streaming runtime
  def canBeEnding: Boolean = false

}
