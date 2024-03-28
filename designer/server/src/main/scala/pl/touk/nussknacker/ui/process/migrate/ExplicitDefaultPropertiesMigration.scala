package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.migration.ProcessMigration

class ExplicitDefaultPropertiesMigration extends ProcessMigration {

  override def description: String = "Set explicit values for default scenario properties"

  override def migrateProcess(canonicalProcess: CanonicalProcess, category: String): CanonicalProcess = {
    val metaData = canonicalProcess.metaData
    metaData.additionalFields.metaDataType match {
      case StreamMetaData.typeName =>
        setDefaultsIfEmpty(
          canonicalProcess,
          Map(
            StreamMetaData.parallelismName            -> "1",
            StreamMetaData.spillStateToDiskName       -> "true",
            StreamMetaData.useAsyncInterpretationName -> "false"
          )
        )
      case LiteStreamMetaData.typeName =>
        setDefaultsIfEmpty(
          canonicalProcess,
          Map(
            LiteStreamMetaData.parallelismName -> "1"
          )
        )
      case _ => canonicalProcess
    }
  }

  def setDefaultsIfEmpty(canonicalProcess: CanonicalProcess, defaultValues: Map[String, String]): CanonicalProcess = {
    val oldProperties = canonicalProcess.metaData.additionalFields.properties
    val newProperties = oldProperties.map { case (k, v) =>
      if (defaultValues.contains(k) && v.isEmpty) {
        k -> defaultValues(k)
      } else {
        k -> v
      }

    }
    canonicalProcess.copy(metaData =
      canonicalProcess.metaData.copy(additionalFields =
        canonicalProcess.metaData.additionalFields.copy(properties = newProperties)
      )
    )
  }

}
