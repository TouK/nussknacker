import DangerousIcon from "@mui/icons-material/Dangerous";
import { useTheme } from "@mui/material";
import { isEmpty } from "lodash";
import React from "react";

import type { ValidationErrors } from "../../../types/validation";
import { styledIcon } from "../Styled";

export const HeaderIcon = ({ errors }: { errors: Partial<ValidationErrors> }) => {
    const theme = useTheme();

    const StyledDangerousIcon = styledIcon(DangerousIcon, theme.palette.error.light);
    return isEmpty(errors.globalErrors) &&
        isEmpty(errors.invalidNodes) &&
        isEmpty(errors.processPropertiesErrors) &&
        isEmpty(errors.testCasesValidationErrors) ? null : (
        <StyledDangerousIcon sx={{ alignSelf: "flex-start" }} />
    );
};
