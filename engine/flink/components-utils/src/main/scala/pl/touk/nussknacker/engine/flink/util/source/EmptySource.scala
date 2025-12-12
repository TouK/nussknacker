package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source.{Boundedness, ReaderOutput, SourceReader, SourceReaderContext}
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.core.io.InputStatus

class EmptySource[OUT](boundedness: Boundedness, typeInformation: TypeInformation[OUT])
    extends SingleSplitSource[OUT]
    with ResultTypeQueryable[OUT] {

  override def getBoundedness: Boundedness = boundedness

  override def createReader(readerContext: SourceReaderContext): SourceReader[OUT, SingleSplitSource.SingleSplit] =
    new SingleSplitSource.Reader[OUT] {
      override def pollNext(output: ReaderOutput[OUT]): InputStatus = InputStatus.END_OF_INPUT
    }

  override def getProducedType: TypeInformation[OUT] = typeInformation

}
