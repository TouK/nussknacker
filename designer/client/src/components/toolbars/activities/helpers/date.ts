import moment from "moment/moment";
import { Moment } from "moment";

export function formatUiDate(date: string) {
    const now = moment(); // Current date and time
    const inputDate = moment(date); // Date to be formatted

    if (inputDate.isSame(now, "day")) {
        return "Today";
    } else if (inputDate.isSame(moment().subtract(1, "days"), "day")) {
        return "Yesterday";
    } else {
        return date;
    }
}

export function formatDate(date: string | Moment) {
    return moment(date).format("YYYY-MM-DD");
}
