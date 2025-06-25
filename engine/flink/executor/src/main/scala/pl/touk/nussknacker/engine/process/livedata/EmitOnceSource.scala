package pl.touk.nussknacker.engine.process.livedata

import org.apache.flink.api.connector.source._
import org.apache.flink.core.io.{InputStatus, SimpleVersionedSerializer}

import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class EmitOnceSource extends Source[String, DummySplit, Void] {
  override def getBoundedness: Boundedness                                                  = Boundedness.BOUNDED
  override def createReader(context: SourceReaderContext): SourceReader[String, DummySplit] = new EmitOnceReader
  override def getSplitSerializer: SimpleVersionedSerializer[DummySplit]                    = DummySplitSerializer

  override def createEnumerator(ctx: SplitEnumeratorContext[DummySplit]): SplitEnumerator[DummySplit, Void] =
    new EmitOnceEnumerator(ctx)

  override def restoreEnumerator(
      ctx: SplitEnumeratorContext[DummySplit],
      checkpoint: Void
  ): SplitEnumerator[DummySplit, Void] = createEnumerator(ctx)

  override def getEnumeratorCheckpointSerializer: SimpleVersionedSerializer[Void] = VoidSerializer
}

object VoidSerializer extends SimpleVersionedSerializer[Void] {
  override def getVersion: Int                                          = 1
  override def serialize(obj: Void): Array[Byte]                        = Array.empty
  override def deserialize(version: Int, serialized: Array[Byte]): Void = null
}

class EmitOnceEnumerator(ctx: SplitEnumeratorContext[DummySplit]) extends SplitEnumerator[DummySplit, Void] {
  private var assigned                                                             = false
  override def start(): Unit                                                       = ()
  override def handleSplitRequest(subtaskId: Int, requesterHostname: String): Unit = assignIfNeeded(subtaskId)
  override def addReader(readerId: Int): Unit                                      = assignIfNeeded(readerId)

  private def assignIfNeeded(subtaskId: Int): Unit = {
    if (!assigned) {
      ctx.assignSplit(new DummySplit(), subtaskId)
      ctx.signalNoMoreSplits(subtaskId)
      assigned = true
    }
  }

  override def addSplitsBack(splits: java.util.List[DummySplit], subtaskId: Int): Unit = ()
  override def snapshotState(checkpointId: Long): Void                                 = null
  override def close(): Unit                                                           = ()
}

class EmitOnceReader extends SourceReader[String, DummySplit] {
  private val emitted        = new AtomicBoolean(false)
  override def start(): Unit = {}

  override def pollNext(output: ReaderOutput[String]): InputStatus = {
    if (emitted.compareAndSet(false, true)) {
      output.collect("init")
      InputStatus.MORE_AVAILABLE
    } else {
      InputStatus.NOTHING_AVAILABLE
    }
  }

  override def isAvailable: CompletableFuture[Void]                          = new CompletableFuture()
  override def addSplits(splits: java.util.List[DummySplit]): Unit           = ()
  override def notifyNoMoreSplits(): Unit                                    = ()
  override def snapshotState(checkpointId: Long): java.util.List[DummySplit] = Collections.emptyList()
  override def close(): Unit                                                 = ()

}
