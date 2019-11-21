package pl.touk.nussknacker.engine.api.dict.static

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.dict.DictRegistry.{DictEntryWithKeyNotExists, DictEntryWithLabelNotExists, DictNotDeclared}
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictEntry, DictQueryService, DictRegistry}

import scala.concurrent.{ExecutionContext, Future}

trait StaticDictRegistry extends DictRegistry {

  protected def declarations: Map[String, DictDefinition]

  override def keyByLabel(dictId: String, label: String): Validated[DictRegistry.DictLookupError, String] = {
    declarations.get(dictId).map(Valid(_)).getOrElse(Invalid(DictNotDeclared(dictId))).andThen {
      case static: StaticDictDefinition =>
        static.keyByLabel.get(label).map(key => Valid(key))
          .getOrElse(Invalid(DictEntryWithLabelNotExists(dictId, label, Some(static.keyByLabel.keys.toList))))
      case definition =>
        handleNotStaticKeyBeLabel(definition, label)
    }
  }

  override def labelByKey(dictId: String, key: String): Validated[DictRegistry.DictLookupError, Option[String]] = {
    declarations.get(dictId).map(Valid(_)).getOrElse(Invalid(DictNotDeclared(dictId))).andThen {
      case static: StaticDictDefinition =>
        static.labelByKey.get(key).map(key => Valid(Some(key)))
          .getOrElse(Invalid(DictEntryWithKeyNotExists(dictId, key, Some(static.labelByKey.keys.toList))))
      case definition =>
        handleNotStaticLabelByKey(definition, key)
    }
  }

  def labels(dictId: String): Validated[DictRegistry.DictNotDeclared, Either[DictDefinition, List[DictEntry]]] = {
    declarations.get(dictId).map(Valid(_)).getOrElse(Invalid(DictNotDeclared(dictId))).map {
      case static: StaticDictDefinition =>
        Right(static.labelByKey.toList.map(DictEntry.apply _ tupled))
      case definition =>
        Left(definition)
    }
  }

  protected def handleNotStaticKeyBeLabel(definition: DictDefinition, label: String): Validated[DictRegistry.DictEntryWithLabelNotExists, String]

  protected def handleNotStaticLabelByKey(definition: DictDefinition, key: String): Validated[DictEntryWithKeyNotExists, Option[String]]

}


trait StaticDictQueryService extends DictQueryService {

  protected def dictRegistry: StaticDictRegistry

  protected def maxResults: Int

  override def queryEntriesByLabel(dictId: String, labelPattern: String)
                                  (implicit ec: ExecutionContext): Validated[DictRegistry.DictNotDeclared, Future[List[DictEntry]]] =
    dictRegistry.labels(dictId).map {
      case Right(staticLabels) =>
        val lowerLabelPattern = labelPattern.toLowerCase
        val filteredEntries = staticLabels.filter(_.label.toLowerCase.contains(lowerLabelPattern)).sortBy(_.label).take(maxResults)
        Future.successful(filteredEntries)
      case Left(nonStaticDefinition) =>
        handleNotStaticQueryEntriesByLabel(nonStaticDefinition, labelPattern)
    }

  protected def handleNotStaticQueryEntriesByLabel(definition: DictDefinition, labelPattern: String)
                                                  (implicit ec: ExecutionContext): Future[List[DictEntry]]

}