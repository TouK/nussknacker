import React from "react";
import { ButtonWithFocus } from "../../../../withFocus";
import { styled, useTheme } from "@mui/material";
import { EditorType } from "./Editor";
import { css } from "@emotion/css";
import CodeIcon from "./icons/code.svg";
import TextIcon from "./icons/code_off.svg";
import ListIcon from "./icons/list.svg";
import ScheduleIcon from "./icons/schedule.svg";
import DateIcon from "./icons/date_range.svg";

export const SwitchButton = styled(ButtonWithFocus)(({ disabled, theme }) => ({
    width: 35,
    height: 35,
    padding: 5,
    backgroundColor: theme.custom.colors.secondaryBackground,
    border: "none",
    opacity: disabled ? 0.5 : 1,
    filter: disabled ? "saturate(0)" : "non",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    "& > svg": {
        width: "100%",
    },
}));

function getTypeIcon(type: EditorType) {
    switch (type) {
        case EditorType.FIXED_VALUES_PARAMETER_EDITOR:
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
    return <Icon className={css({ color: theme.custom.colors.ok })} />;
}

export const RawEditorIcon = CodeIcon;
