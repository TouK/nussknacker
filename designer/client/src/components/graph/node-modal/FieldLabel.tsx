import styled from "@emotion/styled";
import React from "react";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import { NodeId, ParameterConfig, ProcessDefinitionData, UIParameter } from "../../../types";
import { LimitedValidationLabel } from "../../common/ValidationLabel";

export function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
    return definitions?.find((param) => param.name === paramName);
}

function getNodeParams(processDefinitionData: ProcessDefinitionData, nodeId: NodeId): Record<string, ParameterConfig> {
    return processDefinitionData.componentsConfig[nodeId]?.params;
}

const Footer = styled(LimitedValidationLabel.withComponent("div"))({
    fontWeight: 500,
    opacity: 0.7,
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
        <div className="node-label" title={paramName}>
            {/* eslint-disable-next-line i18next/no-literal-string */}
            {label}:{parameter ? <Footer title={readableType}>{readableType}</Footer> : null}
        </div>
    );
}
