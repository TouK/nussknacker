import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { validateNodeData } from "../../../../actions/nk";
import { success } from "../../../../actions/notificationActions";
import HttpService from "../../../../http/HttpService";
import { getProcessName, getScenarioGraph } from "../../../../reducers/selectors/graph";
import type { NodeType } from "../../../../types";
import { getFindAvailableBranchVariables, getFindAvailableVariables } from "../NodeDetailsContent/selectors";
import { StyledLoadingButton } from "./StyledLoadingButton";

interface Props {
    node: NodeType;
}
export const GenerateNewEndpoint = ({ node }: Props) => {
    const { t } = useTranslation();

    const dispatch = useDispatch();
    const scenarioName = useSelector(getProcessName);
    const scenarioGraph = useSelector(getScenarioGraph);

    const getBranchVariableTypes = useSelector(getFindAvailableBranchVariables);
    const findAvailableVariables = useSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(node.id), [findAvailableVariables, node.id]);

    const handleSendHttpRequest = useCallback(async () => {
        try {
            await HttpService.nodeActions(scenarioName, "generate-endpoint", node);

            dispatch(
                validateNodeData(scenarioName, {
                    outgoingEdges: scenarioGraph.edges,
                    nodeData: node,
                    processProperties: scenarioGraph.properties,
                    branchVariableTypes: getBranchVariableTypes(node.id),
                    variableTypes,
                }),
            );
            dispatch(success(t("nodeActions.generateEndpoint.success", "Endpoint created and added to the list successfully.")));
        } catch (error) {
            console.error("Error sending request:", error);
        }
    }, [dispatch, getBranchVariableTypes, node, scenarioGraph.edges, scenarioGraph.properties, scenarioName, t, variableTypes]);

    return <StyledLoadingButton title={t("node.actions.generateNewEndpoint", "Generate New Endpoint")} action={handleSendHttpRequest} />;
};
