import { FormLabel, styled } from "@mui/material";
import React from "react";
import { UIParameter } from "../../../types";
import NodeTip from "./NodeTip";
import InfoIcon from "@mui/icons-material/Info";
import ProcessUtils from "../../../common/ProcessUtils";

export function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
    return definitions?.find((param) => param.name === paramName);
}

const Footer = styled("div")({
    fontWeight: 500,
    opacity: 0.7,
    display: "-webkit-box",
    WebkitLineClamp: 3,
    WebkitBoxOrient: "vertical",
    overflow: "hidden",
});

export const StyledNodeTip = styled(NodeTip)`
    margin: 0 8px;
    flex: 1;
    svg {
        width: 16px;
        height: 16px;
    }
`;

export function FieldLabel({
    title,
    label,
    footerText,
    hintText,
}: {
    title: string;
    label: string;
    footerText?: string;
    hintText?: string;
}): JSX.Element {
    return (
        <>
            <FormLabel title={title}>
                <div>
                    <div>{label}:</div>
                    {footerText ? <Footer title={footerText}>{footerText}</Footer> : null}
                </div>
                {hintText && <StyledNodeTip title={hintText} icon={<InfoIcon />} />}
            </FormLabel>
        </>
    );
}

export function ParamFieldLabel({
    paramName,
    parameterDefinitions,
}: {
    paramName: string;
    parameterDefinitions: UIParameter[];
}): JSX.Element {
    const parameter = findParamDefinitionByName(parameterDefinitions, paramName);
    const label = parameter?.label || paramName; // Fallback to paramName is for hard-coded parameters like Description
    const readableType = ProcessUtils.humanReadableType(parameter?.typ || null);
    return <FieldLabel title={paramName} label={label} footerText={parameter && readableType} hintText={parameter?.hintText} />;
}
