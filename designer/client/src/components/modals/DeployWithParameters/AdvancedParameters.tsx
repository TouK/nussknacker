import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { groupBy, mapValues } from "lodash";
import { Skeleton, Typography } from "@mui/material";
import { AdvancedParametersSection } from "./AdvancedParametersSection";
import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { GroupedActionParameter } from "./GroupedActionParameter";
import HttpService, { NodesDeploymentData } from "../../../http/HttpService";
import { ActionNodeParameters } from "../../../types/action";
import { useErrorBoundary } from "react-error-boundary";

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
    const [parametersDefinition, setParametersDefinition] = useState<ActionNodeParameters[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const { showBoundary } = useErrorBoundary();

    const getActionParameters = useCallback(async () => {
        setIsLoading(true);
        await HttpService.getActionParameters(processName)
            .then((response) => {
                const definition = response.data.actionNameToParameters["DEPLOY"] || ([] as ActionNodeParameters[]);
                const initialValues = initialNodesData(definition);
                setParametersDefinition(definition);
                setParametersValues(initialValues);
            })
            .catch((e) => {
                showBoundary(e.message);
            })
            .finally(() => {
                setIsLoading(false);
            });
    }, [processName, setParametersValues, showBoundary]);

    useEffect(() => {
        getActionParameters();
    }, [processName, getActionParameters]);

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
            {isLoading && <Skeleton variant="text" sx={{ fontSize: "1.25rem", mt: 1.5 }} width={"50%"} />}
            {!isLoading &&
                Object.entries(groupBy(parametersDefinition, (def) => def.componentId)).map(([componentId, nodeParameters]) => (
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
