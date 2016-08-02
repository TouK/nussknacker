package pl.touk.esp.engine.api

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction

trait Source[T] {

  def typeInformation: TypeInformation[T]

  def toFlinkSource: SourceFunction[T]

  def extractTime(in: T) : Long = System.currentTimeMillis()

}

trait SourceFactory[T] {

  def create(processMetaData: MetaData, parameters: Map[String, String]): Source[T]

}

object SourceFactory {

  def noParam[T](source: Source[T]): SourceFactory[T] = new SourceFactory[T] {
    override def create(processMetaData: MetaData, parameters: Map[String, String]): Source[T] = source
  }

}