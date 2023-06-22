import { NodeId, ParameterConfig, ProcessDefinitionData, TypingResult, UIParameter } from "../../../types";
import { useSelector } from "react-redux";
import { getProcessDefinitionData } from "../../../reducers/selectors/settings";
import ProcessUtils from "../../../common/ProcessUtils";
import React from "react";
import { css, cx } from "@emotion/css";

export function findParamDefinitionByName(definitions: UIParameter[], paramName: string): UIParameter {
    return definitions?.find((param) => param.name === paramName);
}

function getNodeParams(processDefinitionData: ProcessDefinitionData, nodeId: NodeId): Record<string, ParameterConfig> {
    return processDefinitionData.componentsConfig[nodeId]?.params;
}

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

    const lineLimitStyle = css({
        display: "-webkit-box",
        WebkitLineClamp: 3,
        WebkitBoxOrient: "vertical",
        overflow: "hidden",
    });

    return (
        <div className="node-label" title={paramName}>
            {label + ":"}
            {parameter && (
                <div className={cx("labelFooter", lineLimitStyle)} title={readableType}>
                    {readableType}
                </div>
            )}
        </div>
    );
}
