package pl.touk.nussknacker.engine.process.helpers

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.util.exception.{BrieflyLoggingExceptionHandler, ConfigurableExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class BaseSampleConfigCreator[T: ClassTag: TypeTag : TypeInformation](sourceList: List[T]) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]]
  = Map(
    "source" -> WithCategories(FlinkSourceFactory.noParam(new CollectionSource[T](new ExecutionConfig, sourceList, None, Typed.fromDetailedType[T]))),
    "noopSource" -> WithCategories(FlinkSourceFactory.noParam(new CollectionSource[T](new ExecutionConfig, List.empty, None, Typed.fromDetailedType[T]))))

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]]
  = Map("mockService" -> WithCategories(new MockService))

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory
  = ExceptionHandlerFactory.noParams(meta => new ConfigurableExceptionHandler(meta, processObjectDependencies, getClass.getClassLoader))

}