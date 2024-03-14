import { Edge, NodeType } from "../../../types";
import { uniq } from "lodash";
import { useSelector } from "react-redux";
import { getScenario } from "../../../reducers/selectors/graph";
import NodeUtils from "../../graph/NodeUtils";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ensureArray } from "../../../common/arrayUtils";

type SelectorResult = { expression: string } | string;
type Selector = (node: NodeType) => SelectorResult | SelectorResult[];
type FilterSelector = { name: string; selector: Selector }[];

const fieldsSelectors: FilterSelector = [
    {
        name: "id",
        selector: (node) => node.id,
    },
    {
        name: "description",
        selector: (node) => node.additionalFields?.description,
    },
    {
        name: "type",
        selector: (node) => node.type,
    },
    {
        name: "paramValue",
        selector: (node) => node.ref?.outputVariableNames && Object.values(node.ref?.outputVariableNames),
    },
    {
        name: "paramName",
        selector: (node) => node.ref?.outputVariableNames && Object.keys(node.ref?.outputVariableNames),
    },
    {
        name: "paramValue",
        selector: (node) => [node.expression, node.exprVal],
    },
    {
        name: "outputValue",
        selector: (node) => [node.outputName, node.output, node.outputVar, node.varName, node.value],
    },
    {
        name: "paramValue",
        selector: (node) => [node.parameters, node.ref?.parameters, node.service?.parameters, node.fields].flat().map((p) => p?.expression),
    },
    {
        name: "paramName",
        selector: (node) => [node.parameters, node.ref?.parameters, node.service?.parameters, node.fields].flat().map((p) => p?.name),
    },
];

function matchFilters(value: SelectorResult, filterValues: string[]): boolean {
    const resolved = typeof value === "string" ? value : value?.expression;
    return filterValues.length && filterValues.every((filter) => resolved?.toLowerCase().includes(filter.toLowerCase()));
}

export const findFields = (filterValues: string[], node: NodeType) => {
    return uniq(
        fieldsSelectors.flatMap(({ name, selector }) =>
            ensureArray(selector(node))
                .filter((v) => matchFilters(v, filterValues))
                .map(() => name),
        ),
    );
};

export function useFilteredNodes(filterValues: string[]): {
    groups: string[];
    node: NodeType;
    edges: Edge[];
}[] {
    const { t } = useTranslation();
    const { scenarioGraph } = useSelector(getScenario);
    const allNodes = NodeUtils.nodesFromScenarioGraph(scenarioGraph);
    const allEdges = NodeUtils.edgesFromScenarioGraph(scenarioGraph);

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
            allNodes
                .map((node) => {
                    const edges = allEdges
                        .filter((e) => e.from === node.id)
                        .filter((e) => matchFilters(e.edgeType?.condition, filterValues));

                    const groups = findFields(filterValues, node)
                        .concat(edges.length ? "edgeExpression" : null)
                        .map((name) => displayNames[name])
                        .filter(Boolean);

                    return { node, edges, groups };
                })
                .filter(({ groups }) => groups.length),
        [displayNames, allEdges, filterValues, allNodes],
    );
}
