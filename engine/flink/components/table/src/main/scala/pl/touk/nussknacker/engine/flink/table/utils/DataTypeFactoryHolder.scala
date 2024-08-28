package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.{EnvironmentSettings, TableConfig}
import org.apache.flink.table.catalog.{
  CatalogManager,
  CatalogStoreHolder,
  DataTypeFactory,
  GenericInMemoryCatalog,
  GenericInMemoryCatalogStore
}

import scala.util.Using

object DataTypeFactoryHolder {

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
