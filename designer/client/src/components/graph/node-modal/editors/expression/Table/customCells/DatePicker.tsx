import { ProvideEditorComponent } from "@glideapps/glide-data-grid/src/internal/data-grid/data-grid-types";
import React, { useMemo } from "react";
import moment from "moment";
import { DTPicker } from "../../../../../../common/DTPicker";
import { SupportedType } from "../TableEditor";
import { CustomCell, EditableGridCell } from "@glideapps/glide-data-grid";

export type DatePickerCellData = { date: string; kind: "date-picker-cell"; format: SupportedType };
export type DatePickerCell = CustomCell<DatePickerCellData>;
export const isDatePickerCell = (cell: DatePickerCell | EditableGridCell): cell is DatePickerCell =>
    (cell.data as DatePickerCellData).kind === "date-picker-cell";

const DATE_FORMAT = "YYYY-MM-DD";
const TIME_FORMAT = "HH:mm:ss";
const MOMENT_FORMAT = `${DATE_FORMAT} ${TIME_FORMAT}`;
const DATE_TIME_DISPLAY_FORMAT = `${DATE_FORMAT}T${TIME_FORMAT}`;

export const DatePicker: ProvideEditorComponent<DatePickerCell> = (props) => {
    const { value, onChange, target } = props;

    const formattedValue = useMemo(() => (value.data?.date ? moment(value.data?.date, MOMENT_FORMAT) : null), [value.data?.date]);
    return (
        <DTPicker
            inputProps={{
                style: { minWidth: target.width, minHeight: target.height, padding: 0 },
                value: value.data.date,
            }}
            timeFormat={value.data.format === "java.time.LocalDateTime" ? TIME_FORMAT : null}
            dateFormat={DATE_FORMAT}
            open={true}
            value={formattedValue}
            onChange={(data) => {
                onChange({
                    ...value,
                    data: {
                        ...value.data,
                        date:
                            typeof data === "string"
                                ? data
                                : data.format(value.data.format === "java.time.LocalDateTime" ? DATE_TIME_DISPLAY_FORMAT : DATE_FORMAT),
                    },
                });
            }}
        />
    );
};
