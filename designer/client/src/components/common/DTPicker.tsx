import { styled } from "@mui/material";
import type { ComponentProps } from "react";
import React, { forwardRef } from "react";
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

type Props = Omit<ComponentProps<typeof DTPickerStyled>, "locale">;

export const DTPicker = forwardRef<DateTimePicker, Props>(function DTPicker({ inputProps, ...props }, ref) {
    const { i18n } = useTranslation();
    return <DTPickerStyled ref={ref} closeOnSelect {...props} inputProps={{ style, ...inputProps }} locale={i18n.language} />;
});
