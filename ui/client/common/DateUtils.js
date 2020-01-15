import Moment from "moment"
import {displayDateFormat} from "../config"

class DateUtils {
    formatRelatively = (dateTimeString) => {
        return Moment(dateTimeString).calendar(null, {sameElse: displayDateFormat})
    }

    formatAbsolutely = (dateTimeString) => {
        return dateTimeString.replace("T", " | ").substring(0, 18)
    }
}

export default new DateUtils()
