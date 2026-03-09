package pl.touk.nussknacker.engine.flink.table.io.definition.discovery

import cats.data.{NonEmptyList, Validated}
import cats.implicits.catsSyntaxValidatedId
import org.apache.flink.table.api._
import org.apache.flink.table.factories.{DynamicTableFactory, FactoryUtil}
import pl.touk.nussknacker.engine.flink.table.io.definition._
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinitionValidationError._

import scala.jdk.OptionConverters.RichOptional
import scala.util.Try

object TableFactoryDiscovery {

  def discoverTableFactory(
      table: TableDefinition,
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition,
      classLoader: ClassLoader
  ): Validated[NonEmptyList[FlinkDataDefinitionError], DynamicTableFactory] =
    table.getConnector match {
      case Some(connectorFromCreateTableDdl) => discoverTableFactory(connectorFromCreateTableDdl, classLoader)
      case None => discoverTableFactoryFromCatalog(table.tableId.getCatalogName, env, flinkDataDefinition)
    }

  private def discoverTableFactoryFromCatalog(
      catalogName: String,
      env: TableEnvironment,
      flinkDataDefinition: FlinkDataDefinition
  ): Validated[NonEmptyList[FlinkDataDefinitionRegistrationError], DynamicTableFactory] = {
    flinkDataDefinition.registerIn(env).map { _ =>
      val catalog = CatalogDiscovery.discoverCatalog(env, flinkDataDefinition, catalogName)
      val factory = catalog.getFactory.toScala.getOrElse(
        throw new IllegalStateException(
          "Catalog for a table which doesn't have explcitly configured connector option value, does not expose its table factory properly"
        )
      )
      factory match {
        case factory: DynamicTableFactory => factory
        case _ =>
          throw new IllegalStateException(
            s"""Catalog of type "$catalogName" uses a factory which is not a DynamicTableFactory"""
          )
      }
    }
  }

  private def discoverTableFactory(connector: String, classLoader: ClassLoader) =
    Try {
      FactoryUtil.discoverFactory(classLoader, classOf[DynamicTableFactory], connector)
    }.fold(ex => ConnectorNotFound(connector, ex).invalidNel, factory => factory.validNel)

}
