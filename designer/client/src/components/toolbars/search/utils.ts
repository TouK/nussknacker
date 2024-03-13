import { Edge, NodeType } from "../../../types";
import { uniq } from "lodash";
import { useSelector } from "react-redux";
import { getScenario } from "../../../reducers/selectors/graph";
import NodeUtils from "../../graph/NodeUtils";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";

type SelectorResult = { expression: string } | string;
type Selector = (data: [NodeType, Edge[]]) => SelectorResult | SelectorResult[];
type FilterSelector = { name: string; selector: Selector }[];

function selectValues(data: [NodeType, Edge[]], selector: Selector): string[] {
    const resolveExpression = (value) => (typeof value === "string" ? value : value?.expression);
    const value = selector(data);
    const result = value instanceof Array ? value.map((v) => resolveExpression(v)) : [resolveExpression(value)];
    return result.filter(Boolean);
}

const selectors: FilterSelector = [
    { name: "id", selector: ([node]) => node.id },
    { name: "description", selector: ([node]) => node.additionalFields?.description },
    {
        name: "type",
        selector: ([node]) => node.type,
    },
    {
        name: "paramValue",
        selector: ([node]) => node.ref?.outputVariableNames && Object.values(node.ref?.outputVariableNames),
    },
    {
        name: "paramName",
        selector: ([node]) => node.ref?.outputVariableNames && Object.keys(node.ref?.outputVariableNames),
    },
    {
        name: "paramValue",
        selector: ([node]) => [node.expression, node.exprVal],
    },
    {
        name: "outputValue",
        selector: ([node]) => [node.outputName, node.output, node.outputVar, node.varName, node.value],
    },
    {
        name: "paramValue",
        selector: ([node]) =>
            [node.parameters, node.ref?.parameters, node.service?.parameters, node.fields].flat().map((p) => p?.expression),
    },
    {
        name: "paramName",
        selector: ([node]) => [node.parameters, node.ref?.parameters, node.service?.parameters, node.fields].flat().map((p) => p?.name),
    },
    {
        name: "edgeExpression",
        selector: ([, edges]) => {
            return edges.map((e) => e.edgeType?.condition);
        },
    },
];

export const findFields = (filterValues: string[], data: [NodeType, Edge[]]) => {
    if (!filterValues?.length) {
        return [];
    }

    return uniq(
        selectors.flatMap(({ name, selector }) =>
            selectValues(data, selector)
                .map((v) => (filterValues.every((f) => v?.toLowerCase().includes(f.toLowerCase())) ? name : null))
                .filter(Boolean),
        ),
    );
};

export function useFilteredNodes(filterValues: string[]): {
    groups: string[];
    data: [NodeType, Edge[]];
}[] {
    const { t } = useTranslation();
    const { scenarioGraph } = useSelector(getScenario);
    const nodes = NodeUtils.nodesFromScenarioGraph(scenarioGraph);
    const edges = NodeUtils.edgesFromScenarioGraph(scenarioGraph);

    const displayNames = useMemo(
        () => ({
            id: t("panels.search.field.id", "Name"),
            description: t("panels.search.field.description", "Description"),
            type: t("panels.search.field.type", "Type"),
            paramName: t("panels.search.field.paramName", "Label"),
            paramValue: t("panels.search.field.paramValue", "Value"),
            outputValue: t("panels.search.field.outputValue", "Output"),
            edgeExpression: t("panels.search.field.edgeExpression", "Edge"),
        }),
        [t],
    );

    return useMemo(
        () =>
            nodes
                .map((node) => {
                    const data: [NodeType, Edge[]] = [node, edges.filter((e) => e.from === node.id)];
                    const groups = findFields(filterValues, data)
                        .map((name) => displayNames[name])
                        .filter(Boolean);
                    return { data, groups };
                })
                .filter(({ groups }) => groups.length),
        [displayNames, edges, filterValues, nodes],
    );
}
