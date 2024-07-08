import { Box, FormLabel, styled } from "@mui/material";
import React from "react";
import ProcessUtils from "../../../common/ProcessUtils";
import { UIParameter } from "../../../types";
import NodeTip from "./NodeTip";
import InfoIcon from "@mui/icons-material/Info";

export function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
    return definitions?.find((param) => param.name === paramName);
}

export const StyledNodeTip = styled(NodeTip)({
    margin: "0 8px",
    flex: 1,
    "& svg": {
        width: 16,
        height: 16,
    },
});

type FieldLabelProps = {
    name: string;
    children?: string;
    type?: string;
    hint?: string;
};

const FieldLabel = ({ name, children, type, hint }: FieldLabelProps) => (
    <FormLabel title={name}>
        <Box>
            {children ? <Box sx={{ "::after": { content: "':'" } }}>{children}</Box> : null}
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
        {hint ? <StyledNodeTip title={hint} icon={<InfoIcon />} /> : null}
    </FormLabel>
);

export function ParamLabel({ paramName, parameterDefinitions }: { paramName: string; parameterDefinitions: UIParameter[] }): JSX.Element {
    const parameter = findParamDefinitionByName(parameterDefinitions, paramName);
    const label = parameter?.label || paramName; // Fallback to paramName is for hard-coded parameters like Description
    const readableType = ProcessUtils.humanReadableType(parameter?.typ || null);
    return (
        <FieldLabel name={paramName} type={readableType} hint={parameter?.hintText}>
            {label}
        </FieldLabel>
    );
}
