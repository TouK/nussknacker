import { ProcessDefinitionData, ScenarioGraph, ScenarioGraphWithName, ValidationResult } from "../../types";
import { alignFragmentWithSchema } from "../../components/graph/utils/fragmentSchemaAligner";
import { fetchProcessDefinition } from "./processDefinitionData";
import { Scenario } from "../../components/Process/types";
import HttpService from "../../http/HttpService";
import { ThunkAction } from "../reduxTypes";

type EditPropertiesAction = {
    type: "EDIT_PROPERTIES";
    validationResult: ValidationResult;
    scenarioGraphAfterChange: ScenarioGraph;
};

type RenameProcessAction = {
    type: "PROCESS_RENAME";
    name: string;
};

export type PropertiesActions = EditPropertiesAction | RenameProcessAction;

// TODO: We synchronize fragment changes with a scenario in case of properties changes. We need to find a better way to hande it
function alignFragmentsNodeWithSchema(scenarioGraph: ScenarioGraph, processDefinitionData: ProcessDefinitionData): ScenarioGraph {
    return {
        ...scenarioGraph,
        nodes: scenarioGraph.nodes.map((node) => {
            return node.type === "FragmentInput" ? alignFragmentWithSchema(processDefinitionData, node) : node;
        }),
    };
}

const calculateProperties = (scenario, changedProperties): ThunkAction<Promise<ScenarioGraphWithName>> => {
    return async (dispatch) => {
        const processDefinitionData = await dispatch(fetchProcessDefinition(scenario.processingType, scenario.isFragment));
        const processWithNewFragmentSchema = alignFragmentsNodeWithSchema(scenario.scenarioGraph, processDefinitionData);

        if (scenario.name !== changedProperties.name) {
            dispatch({ type: "PROCESS_RENAME", name: changedProperties.name });
        }

        return {
            processName: changedProperties.name,
            scenarioGraph: { ...processWithNewFragmentSchema, properties: changedProperties },
        };
    };
};

export function editProperties(scenario: Scenario, changedProperties): ThunkAction {
    return async (dispatch) => {
        const { processName, scenarioGraph } = await dispatch(calculateProperties(scenario, changedProperties));
        const response = await HttpService.validateProcess(scenario.name, processName, scenarioGraph);

        dispatch({
            type: "EDIT_PROPERTIES",
            validationResult: response.data,
            scenarioGraphAfterChange: scenarioGraph,
        });
    };
}
