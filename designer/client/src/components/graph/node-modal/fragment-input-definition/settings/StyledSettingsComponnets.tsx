import React from "react";
import { Switch, Typography, styled } from "@mui/material";
import { variables } from "../../../../../stylesheets/variables";
import { NodeRow } from "../../../node-modal/NodeDetailsContent/NodeStyled";

export const SettingsWrapper = styled("div")`
    padding: 10px;
    border: 1px solid #ffffff1f;
    width: 100%;
    display: flex;
    flex-direction: column;
    margin-bottom: 20px;
`;

export const SettingLabelStyled = styled("div")`
    font-family: Open Sans;
    color: ${variables.modalLabelTextColor};
    font-size: 12px;
    font-weight: 400;
    line-height: 16px;
    letter-spacing: -0.01em;
    text-align: left;
    align-items: center;
    display: flex;
    flex-basis: 30%;
`;

export const SettingRow = styled(NodeRow)`
    align-items: center;
`;

export const CustomSwitch = styled(Switch)`
    input[type="checkbox"] {
        all: initial !important;
    }
    input[type="checkbox"]:after {
        all: initial !important;
    }
`;

export const SyledFormControlLabel = styled(Typography)`
    font-family: Open Sans;
    font-size: 12px;
    font-weight: 400;
    line-height: 18px;
    letter-spacing: 0.15000000596046448px;
    text-align: left;
`;

export const fieldLabel = (label: string) => <SettingLabelStyled>{label}</SettingLabelStyled>;
