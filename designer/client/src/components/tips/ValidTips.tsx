import React from "react";
import TestingMode from "../../assets/img/icons/testingMode.svg";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import HourglassEmptyIcon from "@mui/icons-material/HourglassEmpty";
import ValidTip from "./ValidTip";
import { useTheme } from "@mui/material";

export default function ValidTips(props: { loading: boolean; hasNeitherErrorsNorWarnings?: boolean; testing?: boolean }): JSX.Element {
    const { loading, hasNeitherErrorsNorWarnings, testing } = props;
    const theme = useTheme();

    return (
        <React.Fragment>
            {loading && (
                <ValidTip icon={HourglassEmptyIcon} message={"Verifying scenario and loading tips"} color={theme.palette.warning.main} />
            )}
            {hasNeitherErrorsNorWarnings && (
                <ValidTip icon={CheckCircleIcon} message={"Everything seems to be OK"} color={theme.palette.success.main} />
            )}
            {testing && <ValidTip icon={TestingMode} message={"Testing mode enabled"} color={"inherit"} />}
        </React.Fragment>
    );
}
