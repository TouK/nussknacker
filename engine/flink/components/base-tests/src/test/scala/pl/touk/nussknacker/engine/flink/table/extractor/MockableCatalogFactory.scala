package pl.touk.nussknacker.engine.flink.table.extractor

import org.apache.flink.table.catalog.{Catalog, GenericInMemoryCatalog}
import org.apache.flink.table.factories.CatalogFactory

class MockableCatalogFactory extends CatalogFactory {

  override def factoryIdentifier(): String = MockableCatalogFactory.catalogName

  override def createCatalog(context: CatalogFactory.Context): Catalog = MockableCatalogFactory.catalog

}

// Warning: this implementation can't be used by concurrent threads
object MockableCatalogFactory {

  private val catalogName = "mockable"

  @volatile
  var catalog: GenericInMemoryCatalog = createCatalog

  private def createCatalog = {
    new GenericInMemoryCatalog(catalogName)
  }

  def resetCatalog(): Unit = {
    catalog = createCatalog
  }

}
