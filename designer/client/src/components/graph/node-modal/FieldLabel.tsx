import { styled } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { NodeId, ParameterConfig, ProcessDefinitionData, UIParameter } from "../../../types";
import { NodeLabelStyled } from "./node";
import NodeTip from "./NodeTip";
import InfoIcon from "@mui/icons-material/Info";

export function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
    return definitions?.find((param) => param.name === paramName);
}

function getNodeParams(processDefinitionData: ProcessDefinitionData, nodeId: NodeId): Record<string, ParameterConfig> {
    return processDefinitionData.componentsConfig[nodeId]?.params;
}

const Footer = styled("div")({
    fontWeight: 500,
    opacity: 0.7,
    display: "-webkit-box",
    WebkitLineClamp: 3,
    WebkitBoxOrient: "vertical",
    overflow: "hidden",
});

const StyledNodeTip = styled(NodeTip)`
    margin: 0 8px;
`;
export function FieldLabel({
    nodeId,
    paramName,
    parameterDefinitions,
}: {
    nodeId: NodeId;
    paramName: string;
    parameterDefinitions: UIParameter[];
}): JSX.Element {
    const processDefinitionData = useSelector(getProcessDefinitionData);
    const params = getNodeParams(processDefinitionData, nodeId);
    const parameter = findParamDefinitionByName(parameterDefinitions, paramName);
    const label = params?.[paramName]?.label ?? paramName;
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
