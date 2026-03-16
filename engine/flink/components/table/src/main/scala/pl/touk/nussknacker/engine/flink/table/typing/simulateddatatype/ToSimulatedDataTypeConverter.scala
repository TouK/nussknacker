package pl.touk.nussknacker.engine.flink.table.typing.simulateddatatype

import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.utils.TypeInfoDataTypeConverter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

object ToSimulatedDataTypeConverter {

  // This is a simulated conversion based on a simulated DataTypeFactory which may work differently in a real Flink
  // cluster, but it may be suitable for example for purposes of type validation
  def toDataType(typingResult: TypingResult): DataType = {
    val typeInfo = TypeInformationDetection.instance.forType(typingResult)
    TypeInfoDataTypeConverter.toDataType(SimulatedDataTypeFactory.instance, typeInfo)
  }

}
