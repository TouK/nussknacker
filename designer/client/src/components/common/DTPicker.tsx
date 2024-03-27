import React from "react";
import DateTimePicker from "react-datetime";
import { useTranslation } from "react-i18next";
import { styled } from "@mui/material";
import { nodeInputCss } from "../NodeInput";
import "./DTPicker.css";

const DTPickerStyled = styled(DateTimePicker)(() => ({
    ...nodeInputCss,
    padding: "0 !important",
}));
const style = {
    background: "none",
    border: "none",
    height: "100%",
    justifyContent: "center",
    width: "100%",
    display: "flex",
    padding: "0 10px",
    alignItems: "center",
};

export function DTPicker({ dateFormat, timeFormat, inputProps, onChange, value }: DateTimePicker.DatetimepickerProps): JSX.Element {
    const { i18n } = useTranslation();
    return (
        <DTPickerStyled
            dateFormat={dateFormat}
            timeFormat={timeFormat}
            inputProps={{ style, ...inputProps }}
            onChange={onChange}
            value={value}
            locale={i18n.language}
        />
    );
}
