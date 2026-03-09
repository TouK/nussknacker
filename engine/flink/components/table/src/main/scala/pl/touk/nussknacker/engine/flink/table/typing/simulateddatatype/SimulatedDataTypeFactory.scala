package pl.touk.nussknacker.engine.flink.table.typing.simulateddatatype

import org.apache.flink.table.api.{EnvironmentSettings, TableConfig}
import org.apache.flink.table.catalog._

import scala.util.Using

private[simulateddatatype] object SimulatedDataTypeFactory {

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
