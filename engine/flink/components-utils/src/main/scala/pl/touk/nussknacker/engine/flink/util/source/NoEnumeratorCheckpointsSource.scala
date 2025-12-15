package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.connector.source.{Source, SourceSplit, SplitEnumerator, SplitEnumeratorContext}
import org.apache.flink.core.io.SimpleVersionedSerializer
import pl.touk.nussknacker.engine.flink.util.source.NoEnumeratorCheckpointsSource.UnitSerializer

abstract class NoEnumeratorCheckpointsSource[T, SplitT <: SourceSplit] extends Source[T, SplitT, Unit] {

  override def getEnumeratorCheckpointSerializer: SimpleVersionedSerializer[Unit] = UnitSerializer

}

object NoEnumeratorCheckpointsSource {

  private object UnitSerializer extends SimpleVersionedSerializer[Unit] {

    override def getVersion: Int = 1

    override def serialize(obj: Unit): Array[Byte] = Array.empty[Byte]

    override def deserialize(version: Int, serialized: Array[Byte]): Unit = ()

  }

}
