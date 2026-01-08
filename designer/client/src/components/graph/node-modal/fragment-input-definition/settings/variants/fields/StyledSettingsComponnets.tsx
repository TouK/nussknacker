import { styled, Switch } from "@mui/material";
import React from "react";

import { FormLabel } from "../../../../editors/FormControl";
import { StyledNodeTip } from "../../../../FieldLabel";

export const SettingsWrapper = styled("div")(({ theme }) => ({
    padding: "10px",
    width: "100%",
    display: "block",
    marginBottom: "20px",
}));

export const SettingLabelStyled = styled(FormLabel)(({ theme }) => ({
    color: theme.palette.text.secondary,
    fontSize: "12px",
    fontWeight: "400",
    ".MuiFormControl-root &": {
        flexBasis: "30% !important",
    },
}));

export const CustomSwitch = styled(Switch)`
    input[type="checkbox"] {
        all: initial !important;
    }
    input[type="checkbox"]:after {
        all: initial !important;
    }
`;

export const FieldLabel = ({ label, required = false, hintText }: { label: string; required?: boolean; hintText?: string }) => (
    <SettingLabelStyled required={required}>
        {label}
        {hintText && <StyledNodeTip variant={"hover"} title={hintText} />}
    </SettingLabelStyled>
);
