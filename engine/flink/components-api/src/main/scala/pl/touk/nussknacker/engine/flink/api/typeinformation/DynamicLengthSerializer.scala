package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeutils.TypeSerializer

trait DynamicLengthSerializer { self: TypeSerializer[_] =>

  override def getLength: Int = -1

}
