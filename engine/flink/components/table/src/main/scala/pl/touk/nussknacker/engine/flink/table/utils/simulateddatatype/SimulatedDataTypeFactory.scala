package pl.touk.nussknacker.engine.flink.table.utils.simulateddatatype

import org.apache.flink.table.api.{EnvironmentSettings, TableConfig}
import org.apache.flink.table.catalog._
import org.apache.flink.table.types.DataType
import org.apache.flink.table.types.utils.TypeInfoDataTypeConverter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import scala.util.Using

object SimulatedDataTypeFactory {

  // This is only a simulated DataTypeFactory which does not have the environment when its used on Flink cluster, but it
  // may be suitable for example for purposes of type validation
  val instance: DataTypeFactory = {
    val config      = TableConfig.getDefault
    val envSettings = EnvironmentSettings.newInstance.build
    val classLoader = getClass.getClassLoader
    Using.resource(
      CatalogManager
        .newBuilder()
        .classLoader(classLoader)
        .config(config)
        .catalogStoreHolder(
          CatalogStoreHolder
            .newBuilder()
            .classloader(classLoader)
            .catalogStore(new GenericInMemoryCatalogStore())
            .config(config)
            .build
        )
        .defaultCatalog(
          envSettings.getBuiltInCatalogName,
          new GenericInMemoryCatalog(
            envSettings.getBuiltInCatalogName,
            envSettings.getBuiltInDatabaseName
          )
        )
        .build
    ) { catalogManager =>
      catalogManager.getDataTypeFactory
    }
  }

}

object ToSimulatedDataTypeConverter {

  // This is a simulated conversion based on a simulated DataTypeFactory which may work differently in a real Flink
  // cluster, but it may be suitable for example for purposes of type validation
  def toDataType(typingResult: TypingResult): DataType = {
    val typeInfo = TypeInformationDetection.instance.forType(typingResult)
    TypeInfoDataTypeConverter.toDataType(SimulatedDataTypeFactory.instance, typeInfo)
  }

}
