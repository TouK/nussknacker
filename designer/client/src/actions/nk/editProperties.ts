import { alignFragmentWithSchema } from "../../components/graph/utils/fragmentSchemaAligner";
import type { Scenario } from "../../components/Process/types";
import { getScenario } from "../../reducers/selectors/graph";
import type { PropertiesType } from "../../types/node";
import type { ProcessDefinitionData, ScenarioGraph } from "../../types/scenarioGraph";
import type { ValidationResult } from "../../types/validation";
import type { ThunkAction } from "../reduxTypes";
import { preApplyValidation } from "./preApplyValidation";
import { fetchProcessDefinition } from "./processDefinitionData";

export type PropertiesActions = {
    type: "EDIT_PROPERTIES";
    validationResult?: ValidationResult;
    scenarioGraphAfterChange: ScenarioGraph;
};

// TODO: We synchronize fragment changes with a scenario in case of properties changes. We need to find a better way to hande it
function alignFragmentsNodeWithSchema(scenarioGraph: ScenarioGraph, processDefinitionData: ProcessDefinitionData): ScenarioGraph {
    return {
        ...scenarioGraph,
        nodes: scenarioGraph.nodes.map((node) => {
            return node.type === "FragmentInput" ? alignFragmentWithSchema(processDefinitionData, node) : node;
        }),
    };
}

const calculateProperties = (scenario: Scenario, changedProperties: PropertiesType): ThunkAction<Promise<ScenarioGraph>> => {
    return async (dispatch) => {
        const processDefinitionData = await dispatch(fetchProcessDefinition(scenario.processingType, scenario.isFragment));
        const processWithNewFragmentSchema = alignFragmentsNodeWithSchema(scenario.scenarioGraph, processDefinitionData);

        return { ...processWithNewFragmentSchema, properties: changedProperties };
    };
};

export function editProperties(changedProperties: PropertiesType, controller?: AbortController): ThunkAction {
    return async (dispatch, getState) => {
        const scenario = getScenario(getState());
        const scenarioGraph = await dispatch(calculateProperties(scenario, changedProperties));
        const response = await dispatch(preApplyValidation(scenario, scenarioGraph, controller));

        dispatch({
            type: "EDIT_PROPERTIES",
            validationResult: response?.data,
            scenarioGraphAfterChange: scenarioGraph,
        });
    };
}
