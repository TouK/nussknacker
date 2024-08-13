import React from "react";
import { Button } from "../../../../FormElements";
import { styled, useTheme } from "@mui/material";
import { EditorType } from "./Editor";
import { css } from "@emotion/css";
import CodeIcon from "./icons/code.svg";
import TextIcon from "./icons/code_off.svg";
import ListIcon from "./icons/list.svg";
import ScheduleIcon from "./icons/schedule.svg";
import DateIcon from "./icons/date_range.svg";

export const SwitchButton = styled(Button)(({ disabled, theme }) => ({
    width: 35,
    height: 35,
    padding: 5,
    backgroundColor: disabled ? theme.palette.action.disabledBackground : theme.palette.background.paper,
    border: "none",
    filter: disabled ? "saturate(0)" : "none",
    pointerEvents: disabled ? "none" : "inherit",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    "&:focus": {
        outline: `1px solid ${theme.palette.primary.main} !important`,
    },
    "& > svg": {
        width: "100%",
    },
}));

function getTypeIcon(type: EditorType) {
    switch (type) {
        case EditorType.FIXED_VALUES_PARAMETER_EDITOR:
        case EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR:
            return ListIcon;
        case EditorType.DURATION_EDITOR:
        case EditorType.CRON_EDITOR:
        case EditorType.PERIOD_EDITOR:
        case EditorType.TIME:
            return ScheduleIcon;
        case EditorType.DATE:
        case EditorType.DATE_TIME:
            return DateIcon;
        case EditorType.SQL_PARAMETER_EDITOR:
            return CodeIcon;
        default:
            return TextIcon;
    }
}

export function SimpleEditorIcon({ type }: { type: EditorType }) {
    const theme = useTheme();
    const Icon = getTypeIcon(type);
    return <Icon className={css({ color: theme.palette.primary.main })} />;
}

export const RawEditorIcon = CodeIcon;
