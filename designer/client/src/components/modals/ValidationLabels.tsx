import { Box, FormHelperText, Stack } from "@mui/material";
import { isEmpty } from "lodash";
import type { ReactNode } from "react";
import React from "react";

import { AskAssistantButton } from "../aiAssistant/components/AiAssistantButton";
import type { FieldError } from "../graph/node-modal/editors/Validators";

type Props = {
    fieldErrors: FieldError[];
    validationLabelInfo?: ReactNode;
};

export default function ValidationLabels(props: Props) {
    const { fieldErrors, validationLabelInfo } = props;

    if (isEmpty(fieldErrors)) {
        if (!validationLabelInfo) return null;
        return (
            <FormHelperText title={typeof validationLabelInfo === "string" ? validationLabelInfo : "Form helper text"}>
                {validationLabelInfo}
            </FormHelperText>
        );
    }

    return (
        <Stack direction="row" spacing={0.5} sx={{ alignItems: "baseline" }}>
            <Stack direction="column">
                {fieldErrors.map((fieldErrors, index) => (
                    <FormHelperText key={index} title={fieldErrors.message} error>
                        {fieldErrors.message}
                    </FormHelperText>
                ))}
            </Stack>
            <Box>
                <AskAssistantButton question={`Explain problems: ${fieldErrors.map(({ message }) => message).join(";")}`} />
            </Box>
        </Stack>
    );
}
