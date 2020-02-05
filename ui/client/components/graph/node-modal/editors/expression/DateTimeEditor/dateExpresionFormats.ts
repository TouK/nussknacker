/* eslint-disable i18next/no-literal-string */
import moment from "moment"

export const getParseRegExp = () => /^T\(java\.time\..*\)\.parse\(['"](.*)['"]\)$/

export const getTimeOnlyFormat = () => "HH:mm:ss"

export const getDateTimeFormat = () => "YYYY-MM-DDTHH:mm:ss"

export const createLocalDate = (m: moment.Moment) => `T(java.time.LocalDate).parse('${m.startOf("day").format("YYYY-MM-DD")}')`

export const createLocalDateTime = (m: moment.Moment) => `T(java.time.LocalDateTime).parse('${m.startOf("minute").format("YYYY-MM-DDTHH:mm")}')`

export const createLocalTime = (m: moment.Moment) => `T(java.time.LocalTime).parse('${m.startOf("second").format("HH:mm:ss")}')`
