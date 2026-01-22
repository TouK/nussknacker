import { Box, FormHelperText, Stack } from "@mui/material";
import { isEmpty, uniqBy } from "lodash";
import type { ReactNode } from "react";
import React from "react";

import { AskAssistantButton } from "../aiAssistant/components/AiAssistantButton";
import type { FieldError } from "../graph/node-modal/editors/Validators";

type Props = {
    fieldErrors: FieldError[];
    validationLabelInfo?: ReactNode;
};

export default function ValidationLabels(props: Props) {
    const { validationLabelInfo } = props;
    const fieldErrors = props.fieldErrors.filter((e) => e.message);

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
                {uniqBy(fieldErrors, "message").map((fieldErrors) => (
                    <FormHelperText key={fieldErrors.message} title={fieldErrors.message} error>
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
