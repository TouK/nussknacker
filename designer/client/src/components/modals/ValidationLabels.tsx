import { FormHelperText } from "@mui/material";
import React from "react";
import { isEmpty } from "lodash";
import { FieldError } from "../graph/node-modal/editors/Validators";

type Props = {
    fieldErrors: FieldError[];
    validationLabelInfo?: string;
};

export default function ValidationLabels(props: Props) {
    const { fieldErrors, validationLabelInfo } = props;

    return (
        <>
            {isEmpty(fieldErrors) && validationLabelInfo ? (
                <FormHelperText title={validationLabelInfo}>{validationLabelInfo}</FormHelperText>
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
