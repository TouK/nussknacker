import React from "react";
import { isEmpty } from "lodash";
import { styledIcon } from "../Styled";
import { ValidationErrors } from "../../../types";
import DangerousIcon from "@mui/icons-material/Dangerous";
import { variables } from "../../../stylesheets/variables";

export const HeaderIcon = ({ errors }: { errors: ValidationErrors }) => {
    const StyledDangerousIcon = styledIcon(DangerousIcon);
    return isEmpty(errors.globalErrors) && isEmpty(errors.invalidNodes) && isEmpty(errors.processPropertiesErrors) ? null : (
        <StyledDangerousIcon sx={{ color: variables.alert.error, alignSelf: "center" }} />
    );
};
