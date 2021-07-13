import Moment from "moment"
import {DISPLAY_DATE_FORMAT} from "../config"

export const formatRelatively = (date: Date | number): string => Moment(date).calendar(null, {sameElse: DISPLAY_DATE_FORMAT})
export const formatAbsolutely = (date: Date | number): string => Moment(date).format(DISPLAY_DATE_FORMAT)
