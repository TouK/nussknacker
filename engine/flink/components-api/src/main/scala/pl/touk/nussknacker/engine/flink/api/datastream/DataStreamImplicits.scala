package pl.touk.nussknacker.engine.flink.api.datastream

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.functions.co.CoMapFunction

object DataStreamImplicits {

  implicit class DataStreamExtension[T](stream: DataStream[T]) {

    @silent("deprecated")
    def mapWithState[R: TypeInformation, S: TypeInformation](fun: (T, Option[S]) => (R, Option[S])): DataStream[R] = {
      val cleanFun                          = stream.getExecutionEnvironment.clean(fun)
      val stateTypeInfo: TypeInformation[S] = implicitly[TypeInformation[S]]
      val serializer: TypeSerializer[S]     = stateTypeInfo.createSerializer(stream.getExecutionConfig)

      val mapper = new RichMapFunction[T, R] with StatefulFunction[T, R, S] {

        override val stateSerializer: TypeSerializer[S] = serializer

        override def map(in: T): R = {
          applyWithState(in, cleanFun)
        }
      }

      stream.map(mapper).returns(implicitly[TypeInformation[R]])
    }

    def connectAndMerge(other: DataStream[T]): SingleOutputStreamOperator[T] = stream
      .connect(other)
      .map(
        new CoMapFunction[T, T, T] {
          override def map1(value: T): T = value
          override def map2(value: T): T = value
        }
      )

  }

}
