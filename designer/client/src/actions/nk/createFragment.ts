import { jsonToFileInFormData } from "../../components/graph/createFragment";
import HttpService from "../../http/HttpService/instance";
import { createUniqueName } from "../../reducers/graph/utils";
import { fetchScenarios, getScenariosNames } from "../../reducers/scenarios";
import { getScenario } from "../../reducers/selectors/graph";
import type { NodeType } from "../../types/node";
import type { ThunkAction } from "../reduxTypes";
import { fetchProcessDefinition } from "./processDefinitionData";

const FRAGMENT_TEMPLATE = {
    metaData: {
        id: "test-frament",
        additionalFields: {
            description: null,
            properties: {
                docsUrl: "",
                componentGroup: "fragments",
                icon: "/assets/components/FragmentInput.svg",
            },
            metaDataType: "FragmentSpecificData",
            showDescription: false,
        },
    },
    nodes: [
        {
            id: "input",
            name: "input",
            parameters: [],
            additionalFields: {
                description: null,
                layoutData: {
                    x: 0,
                    y: 0,
                },
            },
            type: "FragmentInputDefinition",
        },
        {
            id: "output",
            name: "output",
            outputName: "output",
            fields: [],
            additionalFields: {
                description: null,
                layoutData: {
                    x: 0,
                    y: 180,
                },
            },
            type: "FragmentOutputDefinition",
        },
    ],
    additionalBranches: [],
    stickyNotes: [],
};

export function createFragment(): ThunkAction<Promise<NodeType | null>> {
    return async (dispatch, getState) => {
        await dispatch(fetchScenarios());
        const scenarios = getScenariosNames(getState());
        const scenario = getScenario(getState());
        const uniqueName = createUniqueName("empty fragment", scenarios);

        const { processingType, engineSetupName, processCategory, processingMode } = scenario;
        await HttpService.createProcess({
            name: uniqueName,
            isFragment: true,
            category: processCategory,
            processingMode: processingMode,
            engineSetupName: engineSetupName,
        });
        const { data } = await HttpService.importScenario(uniqueName, jsonToFileInFormData(FRAGMENT_TEMPLATE));
        await HttpService.saveProcess(uniqueName, data.scenarioGraph, "import placeholder data", []);
        const { componentGroups } = await dispatch(fetchProcessDefinition(processingType, false));
        const component = componentGroups
            .find((g) => g.name.toLowerCase() === "fragments")
            ?.components.find((c) => c.componentId === `fragment-${uniqueName}`);

        return component ? { ...component.node, id: component.label, name: component.label } : null;
    };
}
