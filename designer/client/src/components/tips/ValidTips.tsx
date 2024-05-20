import React from "react";
import TestingMode from "../../assets/img/icons/testingMode.svg";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ValidTip from "./ValidTip";
import { useTheme } from "@mui/material";

export default function ValidTips(props: { hasNeitherErrorsNorWarnings?: boolean; testing?: boolean }): JSX.Element {
    const { hasNeitherErrorsNorWarnings, testing } = props;
    const theme = useTheme();

    return (
        <React.Fragment>
            {hasNeitherErrorsNorWarnings && (
                <ValidTip icon={CheckCircleIcon} message={"Everything seems to be OK"} color={theme.palette.success.main} />
            )}
            {testing && <ValidTip icon={TestingMode} message={"Testing mode enabled"} color={"inherit"} />}
        </React.Fragment>
    );
}
