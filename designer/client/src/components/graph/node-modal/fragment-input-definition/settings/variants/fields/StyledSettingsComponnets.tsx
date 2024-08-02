import React from "react";
import { alpha, css, FormLabel, styled, Switch } from "@mui/material";
import InfoIcon from "@mui/icons-material/Info";
import { StyledNodeTip } from "../../../../FieldLabel";

import { blendLighten } from "../../../../../../../containers/theme/helpers";

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
    flexBasis: "30%",
}));

export const ListItemContainer = styled("div")`
    width: 100%;
    display: flex;
    justify-content: flex-start;
`;

export const ListItemWrapper = styled("div")(
    ({ theme }) => css`
        display: flex;
        justify-content: flex-start;
        max-height: 100px;
        flex-wrap: wrap;
        overflow: auto;
        margin-top: 10px;
        ::-webkit-scrollbar-track {
            width: 15px;
            height: 100px;
            background: ${blendLighten(theme.palette.background.paper, 0.5)};
        }
        ::-webkit-scrollbar-thumb {
            background: ${alpha(theme.palette.background.paper, 0.85)};
            background-clip: content-box;
            border: 3.5px solid transparent;
            border-radius: 100px;
            height: 60px;
        }

        ::-webkit-scrollbar-thumb:hover {
            background: ${theme.palette.action.hover};
        }
        ::-webkit-scrollbar {
            width: 15px;
            height: 100px;
        }
    `,
);

export const CustomSwitch = styled(Switch)`
    input[type="checkbox"] {
        all: initial !important;
    }
    input[type="checkbox"]:after {
        all: initial !important;
    }
`;

export const fieldLabel = ({ label, required = false, hintText }: { label: string; required?: boolean; hintText?: string }) => (
    <SettingLabelStyled required={required}>
        {label}
        {hintText && <StyledNodeTip title={hintText} icon={<InfoIcon />} />}
    </SettingLabelStyled>
);
