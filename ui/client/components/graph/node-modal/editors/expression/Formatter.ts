import {startsWith} from "lodash"
import moment from "moment"
import {Duration} from "./Duration/DurationEditor"
import {Period} from "./Duration/PeriodEditor"
import {CronExpression} from "./Cron/CronEditor";

export type Formatter = {
  encode: (value: any) => string,
  decode: (value: string) => any,
}

export enum SpelFormatterType {
  String = "java.lang.String",
  Duration = "java.time.Duration",
  Period = "java.time.Period",
  Cron = "com.cronutils.model.Cron",
  Time = "java.time.LocalTime",
  Date = "java.time.LocalDate",
  DateTime = "java.time.LocalDateTime",
}

const defaultQuotationMark = "'"
const valueQuotationMark = value => value.charAt(0)

const valueStartsWithQuotationMark = (value) => startsWith(value, "\"") || startsWith(value, "\'")

const quotationMark = value => valueStartsWithQuotationMark(value) ? valueQuotationMark(value) : defaultQuotationMark

const stringSpelFormatter: Formatter = {
  encode: value => quotationMark(value) + value + quotationMark(value),
  decode: value => value.substring(1, value.length - 1),
}

const spelDurationFormatter: Formatter = {
  encode: (value: Duration) => `T(java.time.Duration).parse('${moment.duration(value).toISOString()}')`,
  decode: value => {
    const result = /^T\(java\.time\.Duration\)\.parse\('(.*?)'\)$/.exec(value)
    return result == null ? null : result[1]
  },
}

const spelPeriodFormatter: Formatter = {
  encode: (value: Period) => `T(java.time.Period).parse('${moment.duration(value).toISOString()}')`,
  decode: value => {
    const result = /^T\(java\.time\.Period\)\.parse\('(.*?)'\)$/.exec(value)
    return result == null ? null : result[1]
  },
}

const spelCronFormatter: Formatter = {
  encode: (value: CronExpression) => `T(com.cronutils.parser.CronParser).parse('${value}')`,
  decode: value => {
    const result = /T\(com\.cronutils\.parser\.CronParser\)\.parse\('(.*?)'\)/.exec(value)
    return result == null ? null : result[1]
  },
}

const spelLocalTimeFormatter: Formatter = {
  encode: (m: moment.Moment) => `T(java.time.LocalTime).parse('${m.startOf("second").format("HH:mm:ss")}')`,
  decode: value => {
    const result = /^T\(java\.time\..*\)\.parse\(['"](.*)['"]\)$/.exec(value)
    return result == null ? null : result[1]
  },
}

const spelDateFormatter: Formatter = {
  encode: (m: moment.Moment) => `T(java.time.LocalDate).parse('${m.startOf("day").format("YYYY-MM-DD")}')`,
  decode: value => {
    const result = /^T\(java\.time\..*\)\.parse\(['"](.*)['"]\)$/.exec(value)
    return result == null ? null : result[1]
  },
}

const spelDateTimeFormatter: Formatter = {
  encode: (m: moment.Moment) => `T(java.time.LocalDateTime).parse('${m.startOf("minute").format("YYYY-MM-DDTHH:mm")}')`,
  decode: value => {
    const result = /^T\(java\.time\..*\)\.parse\(['"](.*)['"]\)$/.exec(value)
    return result == null ? null : result[1]
  },
}

export const defaultFormatter: Formatter = {
  encode: value => value,
  decode: value => value,
}

export const spelFormatters: Record<SpelFormatterType, Formatter> = {
  [SpelFormatterType.String]: stringSpelFormatter,
  [SpelFormatterType.Duration]: spelDurationFormatter,
  [SpelFormatterType.Period]: spelPeriodFormatter,
  [SpelFormatterType.Cron]: spelCronFormatter,
  [SpelFormatterType.Time]: spelLocalTimeFormatter,
  [SpelFormatterType.Date]: spelDateFormatter,
  [SpelFormatterType.DateTime]: spelDateTimeFormatter,
}
