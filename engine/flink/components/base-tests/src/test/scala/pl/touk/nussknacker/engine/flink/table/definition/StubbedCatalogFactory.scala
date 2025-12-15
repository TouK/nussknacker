package pl.touk.nussknacker.engine.flink.table.definition

import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.catalog.{Catalog, CatalogTable, GenericInMemoryCatalog, ObjectPath}
import org.apache.flink.table.factories.CatalogFactory

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

class StubbedCatalogFactory extends CatalogFactory {

  override def factoryIdentifier(): String = StubbedCatalogFactory.catalogName

  override def createCatalog(context: CatalogFactory.Context): Catalog = StubbedCatalogFactory.catalog

}

object StubbedCatalogFactory {

  val catalogName = "stubbed"

  val sampleBoundedTablePath: ObjectPath = ObjectPath.fromString("default.sample_bounded_table")

  val sampleBoundedTableNumberOfRows: Int = 10

  val sampleColumnName = "fooColumn"

  private val catalog: GenericInMemoryCatalog = populateCatalog(new GenericInMemoryCatalog(catalogName))

  private def populateCatalog(inMemoryCatalog: GenericInMemoryCatalog): GenericInMemoryCatalog = {
    val sampleBoundedTable = CatalogTable.newBuilder
      .schema(Schema.newBuilder().column(sampleColumnName, DataTypes.STRING()).build())
      .partitionKeys(List.empty[String].asJava)
      .options(
        Map(
          "connector" -> "datagen",
          // to make it bounded
          "number-of-rows" -> sampleBoundedTableNumberOfRows.toString
        ).asJava
      )
      .build
    inMemoryCatalog.createTable(sampleBoundedTablePath, sampleBoundedTable, false)
    inMemoryCatalog
  }

}
