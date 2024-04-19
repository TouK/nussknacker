import React from "react";
import { isEmpty } from "lodash";
import { styledIcon } from "../Styled";
import { ValidationErrors } from "../../../types";
import DangerousIcon from "@mui/icons-material/Dangerous";
import { useTheme } from "@mui/material";

export const HeaderIcon = ({ errors }: { errors: ValidationErrors }) => {
    const theme = useTheme();

    const StyledDangerousIcon = styledIcon(DangerousIcon, theme.palette.error.light);
    return isEmpty(errors.globalErrors) && isEmpty(errors.invalidNodes) && isEmpty(errors.processPropertiesErrors) ? null : (
        <StyledDangerousIcon sx={{ alignSelf: "flex-start" }} />
    );
};
