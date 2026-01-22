import { produce } from "immer";

import { determineComponentId } from "../../../common/componentUtils";
import type { UIParameter } from "../../../types/definition";
import { ParameterCategory } from "../../../types/definition";
import type { NodeType, PropertiesType } from "../../../types/node";
import type { PropertiesConfig, PropertiesConfigKeys } from "../../../types/scenarioGraph";
import type { NodeValidationError } from "../../../types/validation";
import { editorsParameters } from "./editors/expression/editorsParameters";

export function isRequestSource(node: NodeType) {
    return determineComponentId(node) === "source-request";
}

function isRequestParameter(name: PropertiesConfigKeys) {
    return ["slug", "inputSchema", "outputSchema"].includes(name);
}

export function cleanProperties(nodeData: NodeType) {
    const parameters = nodeData.ref.parameters.filter(({ name }) => !isRequestParameter(name));
    return { ...nodeData, ref: { ...nodeData.ref, parameters } };
}

export function nodePropertiesToScenarioProperties(nodeData: NodeType) {
    const parameters = nodeData.ref.parameters.filter(({ name }) => isRequestParameter(name));
    return Object.fromEntries(parameters.map((p) => [p.name, p.expression.expression]));
}

export function appendNodeDataToProperties(processProperties: PropertiesType, nodeData: NodeType): PropertiesType {
    return produce(processProperties, (draft) => {
        draft.additionalFields.properties = {
            ...draft.additionalFields.properties,
            ...nodePropertiesToScenarioProperties(nodeData),
        };
    });
}

export function scenarioPropertiesToNodeProperties(properties: { inputSchema: string; outputSchema: string; slug: string }) {
    return Object.keys(properties)
        .filter((name) => isRequestParameter(name))
        .map((name) => {
            const expression = { expression: properties[name] };
            return { name, expression };
        });
}

export function getScenarioPropertiesDef(properties: PropertiesConfig, order: string[]) {
    return order
        .filter((name) => isRequestParameter(name))
        .map((name): UIParameter => {
            const { defaultValue, editor, hintText, label } = properties[name];
            return {
                name,
                label,
                hintText,
                defaultValue: { expression: defaultValue, language: editorsParameters[editor.type].language },
                editors: [editor],
                typ: null,
                additionalVariables: {},
                variablesToHide: [],
                category: name === "inputSchema" ? ParameterCategory.Standard : ParameterCategory.Advanced,
            };
        });
}

export function appendPropertiesErrors(errors, propertiesErrors: NodeValidationError[]) {
    return errors.concat(propertiesErrors).map((e) => {
        if (!e.fieldName) {
            const match = e.description.match(/Error at parsing "(\w+)":/);
            if (match) {
                const fieldName = match[1];
                return { ...e, fieldName };
            }
        }
        return e;
    });
}
