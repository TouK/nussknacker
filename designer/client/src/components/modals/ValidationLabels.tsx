import { FormHelperText } from "@mui/material";
import { isEmpty } from "lodash";
import type { ReactNode } from "react";
import React from "react";

import type { FieldError } from "../graph/node-modal/editors/Validators";

type Props = {
    fieldErrors: FieldError[];
    validationLabelInfo?: ReactNode;
};

export default function ValidationLabels(props: Props) {
    const { fieldErrors, validationLabelInfo } = props;

    return (
        <>
            {isEmpty(fieldErrors) && validationLabelInfo ? (
                <FormHelperText title={typeof validationLabelInfo === "string" ? validationLabelInfo : "Form helper text"}>
                    {validationLabelInfo}
                </FormHelperText>
            ) : (
                fieldErrors.map((fieldErrors, index) => (
                    <FormHelperText key={index} title={fieldErrors.message} error>
                        {fieldErrors.message}
                    </FormHelperText>
                ))
            )}
        </>
    );
}
