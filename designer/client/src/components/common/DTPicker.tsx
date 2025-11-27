import type { SxProps } from "@mui/material";
import { styled } from "@mui/material";
import React from "react";
import DateTimePicker from "react-datetime";
import { useTranslation } from "react-i18next";

import { nodeInputCss } from "../NodeInput";

const DTPickerStyled = styled(DateTimePicker)(nodeInputCss, { padding: 0 });
const style = {
    height: "100%",
    justifyContent: "center",
    width: "100%",
    display: "flex",
    padding: "0 10px",
    alignItems: "center",
};

export function DTPicker({
    dateFormat,
    timeFormat,
    inputProps,
    onChange,
    value,
    open,
    sx,
}: DateTimePicker.DatetimepickerProps & { sx?: SxProps }): React.JSX.Element {
    const { i18n } = useTranslation();
    return (
        <DTPickerStyled
            open={open}
            dateFormat={dateFormat}
            timeFormat={timeFormat}
            inputProps={{ style, onFocusCapture: (e) => e.stopPropagation(), ...inputProps }}
            onChange={onChange}
            value={value}
            locale={i18n.language}
            sx={sx}
        />
    );
}
