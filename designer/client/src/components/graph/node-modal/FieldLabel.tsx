import styled from "@emotion/styled";
import React from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { NodeId, ParameterConfig, ProcessDefinitionData, UIParameter } from "../../../types";
import { NodeLabelStyled } from "./fragment-input-definition/NodeStyled";

export function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
    return definitions?.find((param) => param.name === paramName);
}

function getNodeParams(processDefinitionData: ProcessDefinitionData, nodeId: NodeId): Record<string, ParameterConfig> {
    return processDefinitionData.componentsConfig[nodeId]?.params;
}

const Footer = styled.div({
    fontWeight: 500,
    opacity: 0.7,
    display: "-webkit-box",
    WebkitLineClamp: 3,
    WebkitBoxOrient: "vertical",
    overflow: "hidden",
});

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
        <NodeLabelStyled title={paramName}>
            {label}:{parameter ? <Footer title={readableType}>{readableType}</Footer> : null}
        </NodeLabelStyled>
    );
}
