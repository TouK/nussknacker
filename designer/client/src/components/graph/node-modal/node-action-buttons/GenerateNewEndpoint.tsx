import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { validateNodeData } from "../../../../actions/nk";
import HttpService from "../../../../http/HttpService";
import { getProcessName, getScenarioGraph } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { NodeType } from "../../../../types";
import { getFindAvailableBranchVariables, getFindAvailableVariables } from "../NodeDetailsContent/selectors";
import { StyledLoadingButton } from "./StyledLoadingButton";

interface Props {
    node: NodeType;
    handleNewEndpointGenerated: (topic: string) => void;
}
export const GenerateNewEndpoint = ({ node, handleNewEndpointGenerated }: Props) => {
    const { t } = useTranslation();

    const dispatch = useAppDispatch();
    const scenarioName = useAppSelector(getProcessName);
    const scenarioGraph = useAppSelector(getScenarioGraph);

    const getBranchVariableTypes = useAppSelector(getFindAvailableBranchVariables);
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(node.id), [findAvailableVariables, node.id]);

    const handleSendHttpRequest = useCallback(async () => {
        try {
            const { result } = await HttpService.nodeActions(scenarioName, "generate-endpoint", node);
            const newTopic = result?.actionName === "GENERATE_ENDPOINT" ? result?.topic?.expression : "";
            await new Promise<void>((resolve) => {
                dispatch(
                    validateNodeData(
                        scenarioName,
                        {
                            outgoingEdges: scenarioGraph.edges,
                            nodeData: node,
                            processProperties: scenarioGraph.properties,
                            branchVariableTypes: getBranchVariableTypes(node.id),
                            variableTypes,
                        },
                        ({ status }) => {
                            if (status === "allowDataUpdate") {
                                handleNewEndpointGenerated(newTopic);
                            }
                            resolve();
                        },
                    ),
                );
            });
        } catch (error) {
            console.error("Error sending request:", error);
        }
    }, [
        dispatch,
        getBranchVariableTypes,
        handleNewEndpointGenerated,
        node,
        scenarioGraph.edges,
        scenarioGraph.properties,
        scenarioName,
        variableTypes,
    ]);

    return <StyledLoadingButton title={t("node.actions.generateNewEndpoint", "Generate New Endpoint")} action={handleSendHttpRequest} />;
};
