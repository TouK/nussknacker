import React from "react";
import TestingMode from "../../assets/img/icons/testingMode.svg";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";

import ValidTip from "./ValidTip";

export default function ValidTips(props: { hasNeitherErrorsNorWarnings?: boolean; testing?: boolean }): JSX.Element {
    const { hasNeitherErrorsNorWarnings, testing } = props;

    return (
        <React.Fragment>
            {hasNeitherErrorsNorWarnings && <ValidTip icon={CheckCircleIcon} message={"Everything seems to be OK"} />}
            {testing && <ValidTip icon={TestingMode} message={"Testing mode enabled"} />}
        </React.Fragment>
    );
}
