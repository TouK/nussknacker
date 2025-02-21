package pl.touk.nussknacker.engine.api.dict.embedded

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.dict.DictRegistry.{
  DictEntryWithKeyNotExists,
  DictEntryWithLabelNotExists,
  DictNotDeclared
}
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictEntry, DictQueryService, DictRegistry}

import scala.concurrent.{ExecutionContext, Future}

abstract class EmbeddedDictRegistry extends DictRegistry {

  protected def declarations: Map[String, DictDefinition]

  override def keyByLabel(dictId: String, label: String): Validated[DictRegistry.DictLookupError, String] = {
    declarations.get(dictId).map(Valid(_)).getOrElse(Invalid(DictNotDeclared(dictId))).andThen {
      case embedded: EmbeddedDictDefinition =>
        embedded.keyByLabel
          .get(label)
          .map(key => Valid(key))
          .getOrElse(Invalid(DictEntryWithLabelNotExists(dictId, label, Some(embedded.keyByLabel.keys.toList))))
      case definition =>
        handleNotEmbeddedKeyBeLabel(dictId, definition, label)
    }
  }

  override def labelByKey(dictId: String, key: String): Validated[DictRegistry.DictLookupError, Option[String]] = {
    declarations.get(dictId).map(Valid(_)).getOrElse(Invalid(DictNotDeclared(dictId))).andThen {
      case embedded: EmbeddedDictDefinition =>
        embedded.labelByKey
          .get(key)
          .map(key => Valid(Some(key)))
          .getOrElse(Invalid(DictEntryWithKeyNotExists(dictId, key, Some(embedded.labelByKey.keys.toList))))
      case definition =>
        handleNotEmbeddedLabelByKey(dictId, definition, key)
    }
  }

  def labels(dictId: String): Validated[DictRegistry.DictNotDeclared, Either[DictDefinition, List[DictEntry]]] = {
    declarations.get(dictId).map(Valid(_)).getOrElse(Invalid(DictNotDeclared(dictId))).map {
      case embedded: EmbeddedDictDefinition =>
        Right(embedded.labelByKey.toList.map(DictEntry.apply _ tupled))
      case definition =>
        Left(definition)
    }
  }

  override def close(): Unit = {}

  protected def handleNotEmbeddedKeyBeLabel(
      dictId: String,
      definition: DictDefinition,
      label: String
  ): Validated[DictRegistry.DictEntryWithLabelNotExists, String]

  protected def handleNotEmbeddedLabelByKey(
      dictId: String,
      definition: DictDefinition,
      key: String
  ): Validated[DictEntryWithKeyNotExists, Option[String]]

}

abstract class EmbeddedDictQueryService extends DictQueryService {

  protected def dictRegistry: EmbeddedDictRegistry

  protected def maxResults: Int

  override def queryEntriesByLabel(dictId: String, labelPattern: String)(
      implicit ec: ExecutionContext
  ): Validated[DictRegistry.DictNotDeclared, Future[List[DictEntry]]] =
    dictRegistry.labels(dictId).map {
      case Right(embeddedLabels) =>
        val lowerLabelPattern = labelPattern.toLowerCase
        val filteredEntries =
          embeddedLabels.filter(_.label.toLowerCase.contains(lowerLabelPattern)).sortBy(_.label).take(maxResults)
        Future.successful(filteredEntries)
      case Left(notEmbeddedDefinition) =>
        handleNotEmbeddedQueryEntriesByLabel(dictId, notEmbeddedDefinition, labelPattern)
    }

  override def queryEntryByKey(dictId: String, key: String)(
      implicit ec: ExecutionContext
  ): Validated[DictRegistry.DictNotDeclared, Future[Option[DictEntry]]] =
    dictRegistry.labels(dictId).map {
      case Right(embeddedLabels) =>
        Future.successful(embeddedLabels.find(_.key == key))
      case Left(notEmbeddedDefinition) =>
        handleNotEmbeddedQueryEntriesByKey(dictId, notEmbeddedDefinition, key)
    }

  protected def handleNotEmbeddedQueryEntriesByLabel(dictId: String, definition: DictDefinition, labelPattern: String)(
      implicit ec: ExecutionContext
  ): Future[List[DictEntry]]

  protected def handleNotEmbeddedQueryEntriesByKey(dictId: String, definition: DictDefinition, key: String)(
      implicit ec: ExecutionContext
  ): Future[Option[DictEntry]]

  override def close(): Unit = {}

}
