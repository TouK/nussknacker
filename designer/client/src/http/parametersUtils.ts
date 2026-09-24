import type { Component } from "../types";

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
    if (!["aggregate-session", "aggregate-sliding", "aggregate-tumbling"].includes(component.node.nodeType)) {
        return component;
    }

    const parameters = component.node.parameters.map((parameter) => {
        switch (parameter.name) {
            case "aggregator":
                return {
                    ...parameter,
                    expression: {
                        ...parameter.expression,
                        expression: "#AGG.map({count: #AGG.countWhen})",
                    },
                };
            case "aggregateBy":
                return {
                    ...parameter,
                    expression: {
                        ...parameter.expression,
                        expression: "{count: true}",
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
