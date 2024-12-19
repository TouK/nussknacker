package pl.touk.nussknacker.engine.flink.table.definition

import com.github.ghik.silencer.silent
import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.catalog.{Catalog, CatalogTable, GenericInMemoryCatalog, ObjectPath}
import org.apache.flink.table.factories.CatalogFactory

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

  @silent("deprecated")
  private def populateCatalog(inMemoryCatalog: GenericInMemoryCatalog): GenericInMemoryCatalog = {
    val sampleBoundedTable = CatalogTable.of(
      Schema.newBuilder().column(sampleColumnName, DataTypes.STRING()).build(),
      null,
      List.empty[String].asJava,
      Map(
        "connector" -> "datagen",
        // to make it bounded
        "number-of-rows" -> sampleBoundedTableNumberOfRows.toString
      ).asJava
    )
    inMemoryCatalog.createTable(sampleBoundedTablePath, sampleBoundedTable, false)
    inMemoryCatalog
  }

}
