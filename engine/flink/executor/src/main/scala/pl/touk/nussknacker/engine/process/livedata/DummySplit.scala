package pl.touk.nussknacker.engine.process.livedata

import org.apache.flink.api.connector.source.SourceSplit
import org.apache.flink.core.io.SimpleVersionedSerializer

class DummySplit extends SourceSplit {
  override def splitId(): String = "dummy"
}

object DummySplitSerializer extends SimpleVersionedSerializer[DummySplit] {
  override def getVersion: Int                                                = 1
  override def serialize(split: DummySplit): Array[Byte]                      = Array(0)
  override def deserialize(version: Int, serialized: Array[Byte]): DummySplit = new DummySplit()
}
