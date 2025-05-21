import type { Component } from "../types";

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
