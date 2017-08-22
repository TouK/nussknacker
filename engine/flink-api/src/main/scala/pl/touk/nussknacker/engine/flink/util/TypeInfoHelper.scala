package pl.touk.nussknacker.engine.flink.util

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.ValueWithContext

object TypeInfoHelper {

  def valueWithContextTypeInfo: TypeInformation[ValueWithContext[Any]] = {
    implicitly[TypeInformation[ValueWithContext[Any]]]
  }

}
