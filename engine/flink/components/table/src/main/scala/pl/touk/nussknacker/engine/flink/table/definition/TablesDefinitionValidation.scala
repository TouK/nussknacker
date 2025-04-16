package pl.touk.nussknacker.engine.flink.table.definition

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits._
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.table.factories.{CatalogFactory, FactoryUtil}
import org.apache.flink.table.factories.FactoryUtil.DefaultCatalogContext
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition.FlinkSqlDdlStatement.{
  CreateCatalog,
  CreateTable
}
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionDiscoveryError.{
  CatalogDiscoveryProblem,
  CatalogNonTransientValidationError
}

import scala.jdk.CollectionConverters._
import scala.util.Try

// This is used by external project
class TablesDefinitionValidation(env: StreamTableEnvironment, classLoader: ClassLoader) {

  def validateWithoutExternalConnections(
      sqlDdlStatements: String,
  ): ValidatedNel[FlinkDataDefinitionError, Unit] = {
    FlinkDdlParser.parse(sqlDdlStatements).andThen { statements =>
      val createTableStatements = statements.collect { case ct: CreateTable => ct }
      val createTableValidationResult = if (createTableStatements.isEmpty) {
        ().validNel
      } else {
        FlinkDataDefinition
          .apply(createTableStatements, None)
          .andThen { definition =>
            new TablesDefinitionDiscovery(env).discoverTables(definition).sequence
          }
          .void
      }

      val createCatalogStatements = statements.collect { case ct: CreateCatalog => ct }
      val createCatalogValidationResult = if (createCatalogStatements.isEmpty) {
        ().validNel
      } else {
        createCatalogStatements.map(cc => validateCatalogWithoutExternalCalls(cc)).sequence.void
      }

      createTableValidationResult.combine(createCatalogValidationResult)
    }
  }

  private def validateCatalogWithoutExternalCalls(
      createCatalog: CreateCatalog
  ): ValidatedNel[FlinkDataDefinitionDiscoveryError, Unit] = {
    val catalogFactory = Try {
      FactoryUtil.discoverFactory(
        classLoader,
        classOf[CatalogFactory],
        createCatalog.catalogType.value
      )
    }.fold(ex => CatalogDiscoveryProblem(createCatalog.catalogType.value, ex).invalidNel, factory => factory.validNel)

    catalogFactory.andThen(factory => {
      val simulatedContext = new DefaultCatalogContext(
        createCatalog.name.value,
        createCatalog.options
          .filter(_.key != "type")
          .map(option => option.key -> option.value)
          .toMap
          .asJava,
        new Configuration(),
        classLoader
      )
      Try {
        factory.createCatalog(simulatedContext)
      }.toEither
        .leftMap(e => NonEmptyList.one(CatalogNonTransientValidationError(e)))
        .toValidated
        .void
    })
  }

}
