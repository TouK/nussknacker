import { Box, styled, Typography } from "@mui/material";
import React from "react";
import { ErrorBoundary } from "react-error-boundary";

import ProcessUtils from "../../../common/ProcessUtils";
import type { UIParameter } from "../../../types/definition";
import { PlaceholderIconFallbackComponent } from "../../common/error-boundary/fallbackComponent/PlaceholderIconFallbackComponent";
import { FormLabel } from "./editors/FormControl";
import { InfoTooltip } from "./editors/InfoTooltip/InfoTooltip";
import { findParamDefinitionByName } from "./parameterHelpers";

export const StyledNodeTip = styled(InfoTooltip)({
    margin: "0 8px",
});

type FieldLabelProps = {
    title?: string;
    label?: string;
    type?: string;
    hintText?: string;
};

export function FieldLabel({ title, label, type, hintText }: FieldLabelProps): React.JSX.Element {
    return (
        <>
            <FormLabel sx={{ lineHeight: "15px" }} title={title || label}>
                <Box>
                    {label ? <Box sx={{ "::after": { content: "':'" } }}>{label}</Box> : null}
                    {type ? (
                        <Typography
                            variant={"caption"}
                            aria-label="Data type"
                            sx={{
                                fontWeight: 500,
                                opacity: 0.7,
                                display: "-webkit-box",
                                WebkitLineClamp: 3,
                                WebkitBoxOrient: "vertical",
                                overflow: "hidden",
                            }}
                            title={type}
                        >
                            {type}
                        </Typography>
                    ) : null}
                </Box>
                {hintText ? <StyledNodeTip variant={"hover"} title={hintText} /> : null}
            </FormLabel>
        </>
    );
}

interface Props {
    paramName: string;
    parameterDefinitions: UIParameter[];
}
function ParamFieldLabelComponent({ paramName, parameterDefinitions }: Props): React.JSX.Element {
    const parameter = findParamDefinitionByName(parameterDefinitions, paramName);
    const label = parameter?.label || paramName; // Fallback to paramName is for hard-coded parameters like Description
    const readableType = ProcessUtils.humanReadableType(parameter?.typ || null);
    return <FieldLabel title={paramName} label={label} type={readableType} hintText={parameter?.hintText} />;
}

export const ParamFieldLabel = (props: Props) => (
    <ErrorBoundary FallbackComponent={PlaceholderIconFallbackComponent}>
        <ParamFieldLabelComponent {...props} />
    </ErrorBoundary>
);
