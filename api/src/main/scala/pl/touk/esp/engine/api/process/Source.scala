package pl.touk.esp.engine.api.process

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.esp.engine.api.MetaData

trait Source[T] {

  def typeInformation: TypeInformation[T]

  def toFlinkSource: SourceFunction[T]

  def timeExtractionFunction : Option[T => Long] = None

}

trait SourceFactory[T] {

  def create(processMetaData: MetaData, parameters: Map[String, String]): Source[T]

}

object SourceFactory {

  def noParam[T](source: Source[T]): SourceFactory[T] = new SourceFactory[T] {
    override def create(processMetaData: MetaData, parameters: Map[String, String]): Source[T] = source
  }

}