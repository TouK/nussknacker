package pl.touk.esp.engine.api

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction

trait Source {

  def typeInformation: TypeInformation[Any]

  def toFlinkSource: SourceFunction[Any]

}

trait SourceFactory {

  def create(processMetaData: MetaData, parameters: Map[String, String]): Source

}

object SourceFactory {

  def noParam(source: Source): SourceFactory = new SourceFactory {
    override def create(processMetaData: MetaData, parameters: Map[String, String]): Source = source
  }

}