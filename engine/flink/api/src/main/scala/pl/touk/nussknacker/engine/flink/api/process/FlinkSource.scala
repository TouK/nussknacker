package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks, TimestampAssigner}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsCompat

/**
  * Source with methods specific for Flink
  * @tparam T - type of event that is generated by this source. This is needed to handle e.g. syntax suggestions in UI
  */
trait FlinkSource[T] extends Source[T] {

  def sourceStream(env: StreamExecutionEnvironment,
                   flinkNodeContext: FlinkCustomNodeContext): DataStream[T]

  //TODO: design better way of handling test data in generic FlinkSource
  //Probably we *still* want to use CollectionSource (and have some custom logic in parser if needed), but timestamps
  //have to be handled here for now
  def timestampAssignerForTest : Option[TimestampAssigner[T]]

}

/**
  * Typical source with methods specific for Flink, user has only to define Source
  * @tparam T - type of event that is generated by this source. This is needed to handle e.g. syntax suggestions in UI
  */
trait BasicFlinkSource[T] extends FlinkSource[T] with ExplicitUidInOperatorsCompat {

  def flinkSourceFunction: SourceFunction[T]

  //TODO: typeInformation can be directly accessed from FlinkSourceFactory?
  def typeInformation: TypeInformation[T]

  def timestampAssigner : Option[TimestampAssigner[T]]

  def timestampAssignerForTest : Option[TimestampAssigner[T]] = timestampAssigner

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[T] = {

    env.setStreamTimeCharacteristic(if (timestampAssigner.isDefined) TimeCharacteristic.EventTime else TimeCharacteristic.IngestionTime)

    val newStart = setUidToNodeIdIfNeed(flinkNodeContext,
      env
        .addSource[T](flinkSourceFunction)(typeInformation)
        .name(s"${flinkNodeContext.metaData.id}-source"))

    timestampAssigner.map {
      case periodic: AssignerWithPeriodicWatermarks[T@unchecked] =>
        newStart.assignTimestampsAndWatermarks(periodic)
      case punctuated: AssignerWithPunctuatedWatermarks[T@unchecked] =>
        newStart.assignTimestampsAndWatermarks(punctuated)
    }.getOrElse(newStart)

  }
}

//Serializable to make Flink happy, e.g. kafkaMocks.MockSourceFactory won't work properly otherwise
abstract class FlinkSourceFactory[T: TypeInformation] extends SourceFactory[T] with Serializable {

  def clazz: Class[T] = typeInformation.getTypeClass

  def typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

}

object FlinkSourceFactory {

  def noParam[T: TypeInformation](source: FlinkSource[T]): FlinkSourceFactory[T] =
    new NoParamSourceFactory[T](source)

  case class NoParamSourceFactory[T: TypeInformation](source: FlinkSource[T]) extends FlinkSourceFactory[T] {
    @MethodToInvoke
    def create(): Source[T] = source
  }

}
