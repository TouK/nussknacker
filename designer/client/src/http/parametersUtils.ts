import { Component } from "../types";

export function fixBranchParametersTemplate({ node, branchParametersTemplate, ...component }: Component): Component {
    // This is a walk-around for having part of node template (branch parameters) outside of itself.
    // See note in DefinitionPreparer on backend side. // TODO remove it after API refactor
    return {
        ...component,
        node: {
            ...node,
            branchParametersTemplate,
        },
        branchParametersTemplate,
    };
}

export function fixAggregateParameters(component: Component): Component {
    if (!["aggregate-session", "aggregate-sliding", "aggregate-tumbling"].includes(component.node.type)) {
        return component;
    }

    const parameters = component.node.parameters.map((parameter) => {
        switch (parameter.name) {
            case "aggregator":
                return {
                    ...parameter,
                    expression: {
                        ...parameter.expression,
                        expression: "#AGG.map({count: #AGG.sum})",
                    },
                };
            case "aggregateBy":
                return {
                    ...parameter,
                    expression: {
                        ...parameter.expression,
                        expression: "{count: 1}",
                    },
                };
        }
        return parameter;
    });

    return {
        ...component,
        node: {
            ...component.node,
            parameters,
        },
    };
}
