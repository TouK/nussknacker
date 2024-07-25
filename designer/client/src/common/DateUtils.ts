import Moment from "moment";
import { DATE_FORMAT, DISPLAY_DATE_FORMAT } from "../config";

export const formatRelatively = (date: string): string => Moment(date).calendar(null, { sameElse: DISPLAY_DATE_FORMAT });
export const formatAbsolutely = (date: string): string => Moment(date).format(DISPLAY_DATE_FORMAT);
export const formatDateTime = (date: string): string => Moment(date).format(DATE_FORMAT);
