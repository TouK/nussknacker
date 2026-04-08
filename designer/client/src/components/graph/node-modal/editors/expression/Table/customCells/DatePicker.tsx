import type { CustomCell, EditableGridCell, ProvideEditorComponent } from "@glideapps/glide-data-grid";
import type { Moment } from "moment";
import moment from "moment";
import React, { useCallback, useMemo, useRef } from "react";
import { useWindowSize } from "rooks";

import { DTPicker } from "../../../../../../common/DTPicker";
import type { SupportedType } from "../TableEditor";

export type DatePickerCellData = { date: string; kind: "date-picker-cell"; format: SupportedType };
export type DatePickerCell = CustomCell<DatePickerCellData>;
export const isDatePickerCell = (cell: DatePickerCell | EditableGridCell): cell is DatePickerCell => {
    const { data } = cell;
    if (typeof data === "object" && "kind" in data) {
        return data.kind === "date-picker-cell";
    }
    return false;
};

const DATE_FORMAT = "YYYY-MM-DD";
const TIME_FORMAT = "HH:mm:ss";
const DATE_TIME_FORMAT = `${DATE_FORMAT}T${TIME_FORMAT}`;

export function toDisplayFormat(date: string): string {
    return date.replace("T", " ");
}

function toValueFormat(date: string | Moment, format: SupportedType): string {
    if (typeof date === "string") return date;
    return date.format(format === "java.time.LocalDateTime" ? DATE_TIME_FORMAT : DATE_FORMAT);
}

export const DatePicker: ProvideEditorComponent<DatePickerCell> = (props) => {
    const { innerHeight } = useWindowSize();
    const { value, onChange, target } = props;

    const formattedValue = useMemo(() => (value.data?.date ? moment(value.data?.date, DATE_TIME_FORMAT) : null), [value.data?.date]);

    const ref = useRef<HTMLInputElement>(null);
    const datePickerTopPosition = useCallback(() => {
        const rect = ref.current?.parentElement.querySelector(".rdtPicker").getBoundingClientRect();
        const pickerHeight = rect?.height || 0;
        const pickerPadding = 3;
        const position = target.height + pickerPadding;
        if (innerHeight <= target.y + target.height + pickerHeight + pickerPadding * 2) {
            return { bottom: position };
        } else {
            return { top: position };
        }
    }, [innerHeight, target.height, target.y]);

    return (
        <DTPicker
            closeOnClickOutside
            closeOnSelect
            inputProps={{
                value: toDisplayFormat(value.data.date),
                autoFocus: true,
                ref,
            }}
            sx={{
                ".form-control": {
                    background: "transparent",
                    border: 0,
                    outline: 0,
                },
                ".rdtPicker": datePickerTopPosition(),
            }}
            timeFormat={value.data.format === "java.time.LocalDateTime" ? TIME_FORMAT : null}
            dateFormat={DATE_FORMAT}
            value={formattedValue}
            onChange={(data) => {
                onChange({
                    ...value,
                    data: {
                        ...value.data,
                        date: toValueFormat(data, value.data.format),
                    },
                });
            }}
        />
    );
};
