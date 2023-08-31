import { Moment } from "moment";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { DTPicker } from "../../common/DTPicker";
import moment from "moment/moment";
import { css, cx } from "@emotion/css";

const dateFormat = "YYYY-MM-DD";
const timeFormat = "HH:mm:ss";

export type PickerInput = Moment | string;

type PickerProps = { label: string; onChange: (date: PickerInput) => void; value: PickerInput };

export function Picker({ label, onChange, value }: PickerProps): JSX.Element {
    const { i18n } = useTranslation();
    const isValid = useMemo(() => moment(value).isValid(), [value]);
    const datePickerStyle = useMemo(
        () => ({
            // TODO: styled
            className: cx("node-input", { "node-input-with-error": !isValid }),
        }),
        [isValid],
    );

    return (
        <>
            <p>{label}</p>
            <div className={cx("node-table", css({ "&&&": { margin: 0 } }))}>
                <DTPicker
                    dateFormat={dateFormat}
                    timeFormat={timeFormat}
                    inputProps={datePickerStyle}
                    onChange={onChange}
                    value={value}
                    locale={i18n.language}
                />
            </div>
        </>
    );
}
