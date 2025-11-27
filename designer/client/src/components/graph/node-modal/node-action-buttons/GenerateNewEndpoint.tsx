import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { validateNodeData } from "../../../../actions/nk/nodeDetails";
import HttpService from "../../../../http/HttpService/instance";
import { getProcessName, getScenarioGraph } from "../../../../reducers/selectors/graph";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { NodeType } from "../../../../types/node";
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
                        {
                            outgoingEdges: scenarioGraph.edges,
                            nodeData: node,
                            branchVariableTypes: getBranchVariableTypes(node.id),
                            variableTypes,
                        },
                        (status) => {
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
    }, [dispatch, getBranchVariableTypes, handleNewEndpointGenerated, node, scenarioGraph.edges, scenarioName, variableTypes]);

    return <StyledLoadingButton title={t("node.actions.generateNewEndpoint", "Generate New Endpoint")} action={handleSendHttpRequest} />;
};
