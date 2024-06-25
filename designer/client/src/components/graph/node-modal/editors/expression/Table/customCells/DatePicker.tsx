import { ProvideEditorComponent } from "@glideapps/glide-data-grid/src/internal/data-grid/data-grid-types";
import React, { useMemo } from "react";
import moment from "moment";
import { DTPicker } from "../../../../../../common/DTPicker";
import { SupportedType } from "../TableEditor";
import { CustomCell, EditableGridCell } from "@glideapps/glide-data-grid";
import { useWindowSize } from "rooks";

export type DatePickerCellData = { date: string; kind: "date-picker-cell"; format: SupportedType };
export type DatePickerCell = CustomCell<DatePickerCellData>;
export const isDatePickerCell = (cell: DatePickerCell | EditableGridCell): cell is DatePickerCell =>
    (cell.data as DatePickerCellData).kind === "date-picker-cell";

const DATE_FORMAT = "YYYY-MM-DD";
const TIME_FORMAT = "HH:mm:ss";
const MOMENT_FORMAT = `${DATE_FORMAT} ${TIME_FORMAT}`;
const DATE_TIME_DISPLAY_FORMAT = `${DATE_FORMAT}T${TIME_FORMAT}`;

export const DatePicker: ProvideEditorComponent<DatePickerCell> = (props) => {
    const { innerHeight } = useWindowSize();
    const { value, onChange, target } = props;

    const formattedValue = useMemo(() => (value.data?.date ? moment(value.data?.date, MOMENT_FORMAT) : null), [value.data?.date]);

    const datePickerTopPosition = useMemo(() => {
        const datePickerHeight = 245.5;
        const dateTimePickerHeight = 275.5;
        const pickerHeight = value.data.format === "java.time.LocalDateTime" ? dateTimePickerHeight : datePickerHeight;
        const pickerPadding = 10;

        const availablePositions = {
            bottom: target.y + target.height + pickerPadding,
            top: target.y - target.height + pickerPadding - pickerHeight,
        };

        return innerHeight <= target.y + pickerHeight + target.height + pickerPadding * 2
            ? availablePositions.top
            : availablePositions.bottom;
    }, [innerHeight, target.height, target.y, value.data.format]);

    return (
        <DTPicker
            inputProps={{
                style: { minWidth: target.width, minHeight: target.height, padding: 0, outline: 0 },
                value: value.data.date,
                autoFocus: true,
                onFocus: (event) => {
                    event.target.select();
                },
            }}
            sx={{ ".rdtPicker": { top: datePickerTopPosition } }}
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
                                ? data || ""
                                : data.format(value.data.format === "java.time.LocalDateTime" ? DATE_TIME_DISPLAY_FORMAT : DATE_FORMAT),
                    },
                });
            }}
        />
    );
};
