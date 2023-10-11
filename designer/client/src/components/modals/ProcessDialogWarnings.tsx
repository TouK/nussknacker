import { css } from "@emotion/css";
import React from "react";
import { useSelector } from "react-redux";
import WarningIcon from "@mui/icons-material/Warning";
import { hasWarnings } from "../../reducers/selectors/graph";
import { IconWithLabel } from "../tips/IconWithLabel";
import { useTheme } from "@mui/material";

function ProcessDialogWarnings(): JSX.Element {
    const processHasWarnings = useSelector(hasWarnings);
    const theme = useTheme();
    return processHasWarnings ? (
        <h5 className={css({ color: theme.custom.colors.warning })}>
            <IconWithLabel icon={WarningIcon} message={"Warnings found - please look at left panel to see details. Proceed with caution"} />
        </h5>
    ) : null;
}

export default ProcessDialogWarnings;
