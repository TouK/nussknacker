import { startsWith } from "lodash";
import moment from "moment";
import { Duration } from "./Duration/DurationEditor";
import { Period } from "./Duration/PeriodEditor";
import { CronExpression } from "./Cron/CronEditor";

type FormatterValueType = string | Duration | Period | moment.Moment | null;
export type Formatter = {
    encode: (value: FormatterValueType) => string;
    decode: (value: string) => FormatterValueType;
};

export enum FormatterType {
    CharSequence = "java.lang.CharSequence",
    String = "java.lang.String",
    Duration = "java.time.Duration",
    Period = "java.time.Period",
    Cron = "com.cronutils.model.Cron",
    Time = "java.time.LocalTime",
    Date = "java.time.LocalDate",
    DateTime = "java.time.LocalDateTime",
}

const defaultQuotationMark = "'";
const valueQuotationMark = (value) => value.charAt(0);

const valueStartsWithQuotationMark = (value) => startsWith(value, '"') || startsWith(value, "'");

const quotationMark = (value) => (valueStartsWithQuotationMark(value) ? valueQuotationMark(value) : defaultQuotationMark);

export const stringSpelFormatter: Formatter = {
    encode: (value) => quotationMark(value) + value + quotationMark(value),
    decode: (value) => value.substring(1, value.length - 1),
};

const spelDurationFormatter: Formatter = {
    encode: (value: Duration) => `T(java.time.Duration).parse('${moment.duration(value).toISOString()}')`,
    decode: (value) => {
        const result = /^T\(java\.time\.Duration\)\.parse\('(.*?)'\)$/.exec(value);
        return result == null ? null : result[1];
    },
};

const durationFormatter: Formatter = {
    encode: (value: Duration) => `${moment.duration(value).toISOString()}`,
    decode: (value) => value,
};

const spelPeriodFormatter: Formatter = {
    encode: (value: Period) => `T(java.time.Period).parse('${moment.duration(value).toISOString()}')`,
    decode: (value) => {
        const result = /^T\(java\.time\.Period\)\.parse\('(.*?)'\)$/.exec(value);
        return result == null ? null : result[1];
    },
};

const periodFormatter: Formatter = {
    encode: (value: Period) => `${moment.duration(value).toISOString()}`,
    decode: (value) => value,
};

//FIXME: this should not work this way. We should either provide some helper, or (even better) create mini-expression-languages for those types and do not try
//to convert to/from spel. Also: this is probably *not* performant enough to be used on per-event basis
const spelCronFormatter: Formatter = {
    encode: (value: CronExpression) =>
        `new com.cronutils.parser.CronParser(T(com.cronutils.model.definition.CronDefinitionBuilder).instanceDefinitionFor(T(com.cronutils.model.CronType).QUARTZ)).parse('${value}')`,
    decode: (value) => {
        const result =
            /new com\.cronutils\.parser\.CronParser\(T\(com\.cronutils\.model\.definition\.CronDefinitionBuilder\)\.instanceDefinitionFor\(T\(com\.cronutils\.model\.CronType\)\.QUARTZ\)\)\.parse\('(.*?)'\)/.exec(
                value,
            );
        return result == null ? null : result[1];
    },
};

const spelLocalTimeFormatter: Formatter = {
    // eslint-disable-next-line i18next/no-literal-string
    encode: (m: moment.Moment) => `T(java.time.LocalTime).parse('${m.startOf("second").format("HH:mm:ss")}')`,
    decode: (value) => {
        const result = /^T\(java\.time\..*\)\.parse\(['"](.*)['"]\)$/.exec(value);
        return result == null ? null : result[1];
    },
};

const localTimeFormatter: Formatter = {
    // eslint-disable-next-line i18next/no-literal-string
    encode: (m: moment.Moment) => `${m.startOf("second").format("HH:mm:ss")}`,
    decode: (value) => value,
};

const spelDateFormatter: Formatter = {
    encode: (m: moment.Moment) => `T(java.time.LocalDate).parse('${m.startOf("day").format("YYYY-MM-DD")}')`,
    decode: (value) => {
        const result = /^T\(java\.time\..*\)\.parse\(['"](.*)['"]\)$/.exec(value);
        return result == null ? null : result[1];
    },
};

const dateFormatter: Formatter = {
    encode: (m: moment.Moment) => `${m.startOf("day").format("YYYY-MM-DD")}`,
    decode: (value) => value,
};

const spelDateTimeFormatter: Formatter = {
    // eslint-disable-next-line i18next/no-literal-string
    encode: (m: moment.Moment) => `T(java.time.LocalDateTime).parse('${m.startOf("minute").format("YYYY-MM-DDTHH:mm")}')`,
    decode: (value) => {
        const result = /^T\(java\.time\..*\)\.parse\(['"](.*)['"]\)$/.exec(value);
        return result == null ? null : result[1];
    },
};

const dateTimeFormatter: Formatter = {
    // eslint-disable-next-line i18next/no-literal-string
    encode: (m: moment.Moment) => `${m.startOf("minute").format("YYYY-MM-DDTHH:mm")}`,
    decode: (value) => value,
};

const defaultFormatter: Formatter = {
    encode: (value: string) => value,
    decode: (value) => value,
};

export const spelFormatters: Record<FormatterType, Formatter> = {
    [FormatterType.CharSequence]: stringSpelFormatter,
    [FormatterType.String]: stringSpelFormatter,
    [FormatterType.Duration]: spelDurationFormatter,
    [FormatterType.Period]: spelPeriodFormatter,
    [FormatterType.Cron]: spelCronFormatter,
    [FormatterType.Time]: spelLocalTimeFormatter,
    [FormatterType.Date]: spelDateFormatter,
    [FormatterType.DateTime]: spelDateTimeFormatter,
};

export const typeFormatters: Record<FormatterType, Formatter> = {
    [FormatterType.Duration]: durationFormatter,
    [FormatterType.Period]: periodFormatter,
    [FormatterType.Time]: localTimeFormatter,
    [FormatterType.Date]: dateFormatter,
    [FormatterType.DateTime]: dateTimeFormatter,
    [FormatterType.CharSequence]: defaultFormatter,
    [FormatterType.String]: defaultFormatter,
    [FormatterType.Cron]: defaultFormatter,
};
