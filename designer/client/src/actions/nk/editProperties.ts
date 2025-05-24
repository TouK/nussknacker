import { alignFragmentWithSchema } from "../../components/graph/utils/fragmentSchemaAligner";
import type { Scenario } from "../../components/Process/types";
import HttpService from "../../http/HttpService";
import { updateValidationResult } from "../../reducers/graph";
import { getNodeResults } from "../../reducers/selectors/graph";
import type { ProcessDefinitionData, PropertiesType, ScenarioGraph, ValidationResult } from "../../types";
import type { ThunkAction } from "../reduxTypes";
import { fetchProcessDefinition } from "./processDefinitionData";

type EditPropertiesAction = {
    type: "EDIT_PROPERTIES";
    validationResult: ValidationResult;
    scenarioGraphAfterChange: ScenarioGraph;
};

export type PropertiesActions = EditPropertiesAction;

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

        return {
            ...processWithNewFragmentSchema,
            properties: changedProperties,
        };
    };
};

export function editProperties(scenario: Scenario, changedProperties: PropertiesType): ThunkAction {
    return async (dispatch, getState) => {
        const scenarioGraph = await dispatch(calculateProperties(scenario, changedProperties));
        const { data } = await HttpService.validateProcess(scenario.name, scenarioGraph.properties.name, scenarioGraph);
        const state = getState();
        const currentNodeResults = getNodeResults(state);
        const validationResult = updateValidationResult(currentNodeResults, data);

        dispatch({
            type: "EDIT_PROPERTIES",
            validationResult,
            scenarioGraphAfterChange: scenarioGraph,
        });
    };
}
