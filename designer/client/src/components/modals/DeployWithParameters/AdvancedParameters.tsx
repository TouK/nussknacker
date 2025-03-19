import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { groupBy, mapValues } from "lodash";
import { Typography } from "@mui/material";
import { AdvancedParametersSection } from "./AdvancedParametersSection";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { GroupedActionParameter } from "./GroupedActionParameter";
import HttpService, { NodesDeploymentData } from "../../../http/HttpService";
import { ActionNodeParameters } from "../../../types/action";
import { useErrorBoundary } from "react-error-boundary";
import { clear, suspend } from "suspend-react";

interface AdvancedParametersProps {
    processName: string;
    expandedState: Record<string, boolean>;
    setExpandedState: (state: Record<string, boolean>) => void;
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
    expandedState,
    setExpandedState,
    setParametersValues,
    parametersValues,
}) => {
    const { t } = useTranslation();
    const { showBoundary } = useErrorBoundary();

    const parametersDefinition: ActionNodeParameters[] | undefined = suspend(async () => {
        return HttpService.getActionParameters(processName)
            .then((response) => {
                const definition = response.data.actionNameToParameters["DEPLOY"] || ([] as ActionNodeParameters[]);
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

    console.log(parametersValues);
    console.log(parametersDefinition);

    if (parametersDefinition?.length === 0) {
        return null;
    }

    return (
        <div>
            <Typography
                sx={(theme) => ({
                    color: theme.palette.primary.main,
                    pt: "1em",
                    textTransform: "uppercase",
                    textDecoration: "none",
                })}
            >
                {t("dialog.advancedParameters.title", "Advanced parameters")}
            </Typography>
            {Object.entries(groupBy(parametersDefinition, (def) => def.componentId)).map(([componentId, nodeParameters]) => (
                <AdvancedParametersSection
                    key={componentId}
                    componentId={componentId}
                    expanded={expandedState[componentId]}
                    onChange={(isExpanded) =>
                        setExpandedState({
                            ...expandedState,
                            [componentId]: isExpanded,
                        })
                    }
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
                </AdvancedParametersSection>
            ))}
        </div>
    );
};
