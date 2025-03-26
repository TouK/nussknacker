import InfoIcon from "@mui/icons-material/Info";
import { Box, FormLabel, styled } from "@mui/material";
import React from "react";
import { ErrorBoundary } from "react-error-boundary";
import ProcessUtils from "../../../common/ProcessUtils";
import { UIParameter } from "../../../types";
import { PlaceholderIconFallbackComponent } from "../../common/error-boundary/fallbackComponent/PlaceholderIconFallbackComponent";
import NodeTip from "./NodeTip";
import { findParamDefinitionByName } from "./parameterHelpers";

export const StyledNodeTip = styled(NodeTip)({
    margin: "0 8px",
    flex: 1,
    "& svg": {
        width: 16,
        height: 16,
    },
});

type FieldLabelProps = {
    title?: string;
    label?: string;
    type?: string;
    hintText?: string;
};

export function FieldLabel({ title, label, type, hintText }: FieldLabelProps): JSX.Element {
    return (
        <>
            <FormLabel title={title || label}>
                <Box>
                    {label ? <Box sx={{ "::after": { content: "':'" } }}>{label}</Box> : null}
                    {type ? (
                        <Box
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
                        </Box>
                    ) : null}
                </Box>
                {hintText ? <StyledNodeTip title={hintText} icon={<InfoIcon />} /> : null}
            </FormLabel>
        </>
    );
}

interface Props {
    paramName: string;
    parameterDefinitions: UIParameter[];
}
function ParamFieldLabelComponent({ paramName, parameterDefinitions }: Props): JSX.Element {
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
