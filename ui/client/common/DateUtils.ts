import Moment from "moment"
import {displayDateFormat} from "../config"

export const formatRelatively = (date: Date | number): string => Moment(date).calendar(null, {sameElse: displayDateFormat})
export const formatAbsolutely = (date: Date | number): string => Moment(date).format(displayDateFormat)
