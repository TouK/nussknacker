package pl.touk.nussknacker.engine.flink.api.process

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction

object StandardFlinkSourceFunctionUtils {

  @silent("deprecated")
  def createSourceStream[Raw](
      env: StreamExecutionEnvironment,
      sourceFunction: SourceFunction[Raw],
      typeInformation: TypeInformation[Raw]
  ): DataStreamSource[Raw] = env.addSource(sourceFunction, typeInformation)

}
