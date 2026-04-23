import { Typography } from "@mui/material";
import { groupBy, mapValues } from "lodash";
import React, { useEffect } from "react";
import { useErrorBoundary } from "react-error-boundary";
import { useTranslation } from "react-i18next";
import { useLocalstorageState } from "rooks";
import { clear, suspend } from "suspend-react";

import HttpService from "../../../http/HttpService/instance";
import type { NodesDeploymentData } from "../../../http/HttpService/types";
import type { ActionNodeParameters } from "../../../types/action";
import { Expandable } from "../../common/Expandable";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { GroupedActionParameter } from "./GroupedActionParameter";

interface AdvancedParametersProps {
    processName: string;
    actionName: string;
    setParametersValues: (values: NodesDeploymentData) => void;
    parametersValues: NodesDeploymentData;
}

function initialNodesData(params: ActionNodeParameters[]): NodesDeploymentData {
    return params.reduce(
        (paramObj, { nodeId, parameters }) => ({
            ...paramObj,
            [nodeId]: mapValues(parameters, (value) => value.defaultValue || ""),
        }),
        {},
    );
}

export const AdvancedParameters: React.FC<AdvancedParametersProps> = ({
    processName,
    actionName,
    setParametersValues,
    parametersValues,
}) => {
    const { t } = useTranslation();
    const { showBoundary } = useErrorBoundary();
    const [expandedState, setExpandedState] = useLocalstorageState("actionParametersExpandedState", {});

    const parametersDefinition: ActionNodeParameters[] | undefined = suspend(async () => {
        return HttpService.getActionParameters(processName)
            .then((response) => {
                const definition = response.data.actionNameToParameters[actionName] || ([] as ActionNodeParameters[]);
                const initialValues = initialNodesData(definition);

                setParametersValues(initialValues);

                return definition;
            })
            .catch((e) => {
                showBoundary(e.message);
            });
    }, [processName]);

    useEffect(() => {
        return () => clear();
    }, []);

    if (parametersDefinition?.length === 0) {
        return null;
    }

    return (
        <div>
            <Typography sx={{ typography: "body1", color: "text.secondary" }}>
                {t("dialog.advancedParameters.title", "Advanced parameters")}
            </Typography>
            {Object.entries(groupBy(parametersDefinition, (def) => def.componentId)).map(([componentId, nodeParameters]) => (
                <Expandable
                    key={componentId}
                    componentId={componentId}
                    expandableTitle={componentId}
                    expanded={(expandedState[componentId] ??= false)}
                    onChange={(isExpanded) =>
                        setExpandedState((prevState) => ({
                            ...prevState,
                            [componentId]: isExpanded,
                        }))
                    }
                    detailsSx={{ padding: 0 }}
                >
                    <NodeTable>
                        {Object.entries(nodeParameters[0].parameters).map(([paramName, paramConfig]) => {
                            return (
                                <GroupedActionParameter
                                    key={paramName}
                                    nodeIds={nodeParameters.map((n) => n.nodeId)}
                                    parameterName={paramName}
                                    parameterConfig={paramConfig}
                                    errors={[]}
                                    isValidating={false}
                                    onChange={(nodeIds, parameterName, newValue) => {
                                        setParametersValues({
                                            ...parametersValues,
                                            ...Object.fromEntries(
                                                nodeIds.map((nodeId) => [
                                                    [nodeId],
                                                    {
                                                        ...parametersValues[nodeId],
                                                        [parameterName]: newValue,
                                                    },
                                                ]),
                                            ),
                                        });
                                    }}
                                    parameterValue={parametersValues[nodeParameters[0].nodeId][paramName] || ""}
                                />
                            );
                        })}
                    </NodeTable>
                </Expandable>
            ))}
        </div>
    );
};
