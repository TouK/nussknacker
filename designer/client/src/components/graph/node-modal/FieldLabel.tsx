import { styled } from "@mui/material";
import React from "react";
import ProcessUtils from "../../../common/ProcessUtils";
import { UIParameter } from "../../../types";
import { NodeLabelStyled } from "./node";
import NodeTip from "./NodeTip";
import InfoIcon from "@mui/icons-material/Info";

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
    svg {
        width: 16px;
        height: 16px;
    }
`;
export function FieldLabel({ paramName, parameterDefinitions }: { paramName: string; parameterDefinitions: UIParameter[] }): JSX.Element {
    const parameter = findParamDefinitionByName(parameterDefinitions, paramName);
    const label = parameter?.label || paramName; // Fallback to paramName is for hard-coded parameters like Description
    const readableType = ProcessUtils.humanReadableType(parameter?.typ || null);

    return (
        <>
            <NodeLabelStyled title={paramName}>
                <div>
                    <div>{label}:</div>
                    {parameter ? <Footer title={readableType}>{readableType}</Footer> : null}
                </div>
                {parameter?.hintText && <StyledNodeTip title={parameter?.hintText} icon={<InfoIcon />} />}
            </NodeLabelStyled>
        </>
    );
}
