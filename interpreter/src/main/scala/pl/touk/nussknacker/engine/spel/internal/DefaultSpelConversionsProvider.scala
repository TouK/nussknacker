package pl.touk.nussknacker.engine.spel.internal

import org.springframework.core.convert.{ConversionService, TypeDescriptor}
import org.springframework.core.convert.converter.{ConditionalConverter, Converter, ConverterFactory}
import org.springframework.core.convert.support.GenericConversionService
import org.springframework.util.{NumberUtils, StringUtils}

import java.nio.charset.Charset
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime}
import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId, ZoneOffset}
import java.util.{Currency, Locale, UUID}

/**
  * This class creates SpEL's ConversionService. We don't use DefaultConversionService because it has some conversions
  * (mainly between similar types) that would cause ambiguity e.g. ObjectToObjectConverter. Also this default service
  * is lack of conversions between String and java time API (ZoneId, ZoneOffset, etc.), it has only support for legacy
  * java Date/Calendar API
  */
object DefaultSpelConversionsProvider extends DefaultSpelConversionsProvider

class DefaultSpelConversionsProvider extends SpelConversionsProvider {

  override def getConversionService: GenericConversionService = {
    val service = new GenericConversionService
    service.addConverterFactory(new NumberToNumberConverterFactory())
    service.addConverter(classOf[String], classOf[ZoneId], (source: String) => ZoneId.of(source))
    service.addConverter(classOf[String], classOf[ZoneOffset], (source: String) => ZoneOffset.of(source))
    service.addConverter(classOf[String], classOf[Locale], (source: String) => StringUtils.parseLocale(source))
    service.addConverter(classOf[String], classOf[Charset], (source: String) => Charset.forName(source))
    service.addConverter(classOf[String], classOf[Currency], (source: String) => Currency.getInstance(source))
    service.addConverter(classOf[String], classOf[UUID], (source: String) => if (StringUtils.hasLength(source)) UUID.fromString(source.trim) else null)
    service.addConverter(classOf[String], classOf[LocalTime], (source: String) => LocalTime.parse(source))
    service.addConverter(classOf[String], classOf[LocalDate], (source: String) => LocalDate.parse(source))
    service.addConverter(classOf[String], classOf[LocalDateTime], (source: String) => LocalDateTime.parse(source))
    service.addConverter(classOf[String], classOf[ChronoLocalDate], (source: String) => LocalDate.parse(source))
    service.addConverter(classOf[String], classOf[ChronoLocalDateTime[_]], (source: String) => LocalDateTime.parse(source))
    // For purpose of concise usage of numbers in spel templates
    service.addConverter(classOf[Number], classOf[String], (source: Number) => source.toString)
    service
  }

}

class NumberToNumberConverterFactory extends ConverterFactory[Number, Number] with ConditionalConverter {
  override def matches(sourceType: TypeDescriptor, targetType: TypeDescriptor): Boolean = sourceType != targetType

  override def getConverter[T <: Number](targetType: Class[T]): Converter[Number, T] =
    (source: Number) => NumberUtils.convertNumberToTargetClass(source, targetType)
}