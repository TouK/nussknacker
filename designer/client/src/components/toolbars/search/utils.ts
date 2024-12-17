import { Edge, NodeType } from "../../../types";
import { isEqual, uniq } from "lodash";
import { useSelector } from "react-redux";
import { getScenario } from "../../../reducers/selectors/graph";
import NodeUtils from "../../graph/NodeUtils";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ensureArray } from "../../../common/arrayUtils";
import { SearchQuery } from "./SearchResults";
import { getComponentGroups } from "../../../reducers/selectors/settings";

type SelectorResult = { expression: string } | string;
type Selector = (node: NodeType) => SelectorResult | SelectorResult[];
type FilterSelector = { name: string; selector: Selector }[];

const fieldsSelectors: FilterSelector = [
    {
        name: "name",

        selector: (node) => node.id,
    },
    {
        name: "description",
        selector: (node) => node.additionalFields?.description,
    },
    {
        name: "type",
        selector: (node) => [node.nodeType, node.ref?.typ, node.ref?.id, node.type, node.service?.id],
    },
    {
        name: "value",
        selector: (node) => node.ref?.outputVariableNames && Object.values(node.ref?.outputVariableNames),
    },
    {
        name: "label",
        selector: (node) => node.ref?.outputVariableNames && Object.keys(node.ref?.outputVariableNames),
    },
    {
        name: "value",
        selector: (node) => [node.expression, node.exprVal, node.value],
    },
    {
        name: "output",
        selector: (node) => [node.outputName, node.output, node.outputVar, node.varName],
    },
    {
        name: "value",
        selector: (node) => [node.parameters, node.ref?.parameters, node.service?.parameters, node.fields].flat().map((p) => p?.expression),
    },
    {
        name: "label",
        selector: (node) => [node.parameters, node.ref?.parameters, node.service?.parameters, node.fields].flat().map((p) => p?.name),
    },
];

function matchFilters(value: SelectorResult, filterValues: string[]): boolean {
    const resolved = typeof value === "string" ? value : value?.expression;
    return filterValues.length && filterValues.some((filter) => filter != "" && resolved?.toLowerCase().includes(filter.toLowerCase()));
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

const findFieldsUsingSelectorWithName = (selectorName: string, filterValues: string[], node: NodeType) => {
    return uniq(
        fieldsSelectors
            .filter((selector) => selector.name == selectorName)
            .flatMap(({ name, selector }) =>
                ensureArray(selector(node))
                    .filter((v) => matchFilters(v, filterValues))
                    .map(() => name),
            ),
    );
};

function useComponentTypes(): Set<string> {
    const componentsGroups = useSelector(getComponentGroups);

    return useMemo(() => {
        return new Set(
            componentsGroups.flatMap((componentGroup) => componentGroup.components).map((component) => component.label.toLowerCase()),
        );
    }, []);
}

export function useNodeTypes(): string[] {
    const { scenarioGraph } = useSelector(getScenario);
    const allNodes = NodeUtils.nodesFromScenarioGraph(scenarioGraph);
    const componentsSet = useComponentTypes();

    return useMemo(() => {
        const nodeSelector = fieldsSelectors.find((selector) => selector.name == "type")?.selector;
        const availableTypes = allNodes
            .flatMap((node) => ensureArray(nodeSelector(node)).filter((item) => item !== undefined))
            .map((selectorResult) => (typeof selectorResult === "string" ? selectorResult : selectorResult?.expression))
            .filter((type) => componentsSet.has(type.toLowerCase()));

        return uniq(availableTypes);
    }, [allNodes]);
}

export function useFilteredNodes(searchQuery: SearchQuery): {
    groups: string[];
    node: NodeType;
    edges: Edge[];
}[] {
    const { t } = useTranslation();
    const { scenarioGraph } = useSelector(getScenario);
    const allNodes = NodeUtils.nodesFromScenarioGraph(scenarioGraph);
    const allEdges = NodeUtils.edgesFromScenarioGraph(scenarioGraph);

    const searchKeys = Object.keys(searchQuery).filter((key) => searchQuery[key as keyof SearchQuery] !== undefined);
    const isSimpleSearch = searchKeys.length === 1 && searchKeys[0] === "plainQuery";

    const displayNames = useMemo(
        () => ({
            name: t("panels.search.field.id", "Name"),
            description: t("panels.search.field.description", "Description"),
            label: t("panels.search.field.label", "Label"),
            value: t("panels.search.field.value", "Value"),
            output: t("panels.search.field.output", "Output"),
            edge: t("panels.search.field.edge", "Edge"),
            type: t("panels.search.field.type", "Type"),
        }),
        [t],
    );

    return useMemo(
        () =>
            allNodes
                .map((node) => {
                    let edges: Edge[] = [];
                    let groups: string[] = [];

                    if (isSimpleSearch) {
                        edges = allEdges
                            .filter((e) => e.from === node.id)
                            .filter((e) => matchFilters(e.edgeType?.condition, [searchQuery.plainQuery]));

                        groups = findFields([searchQuery.plainQuery], node)
                            .concat(edges.length ? "edge" : null)
                            .map((name) => displayNames[name])
                            .filter(Boolean);

                        return { node, edges, groups };
                    } else {
                        const edgesAux = allEdges
                            .filter((e) => e.from === node.id)
                            .filter((e) => matchFilters(e.edgeType?.condition, [searchQuery.plainQuery]));

                        const groupsAux: string[] = findFields([searchQuery.plainQuery], node)
                            .concat(edgesAux.length ? "edge" : null)
                            .map((name) => displayNames[name])
                            .filter(Boolean);

                        edges =
                            "edge" in searchQuery
                                ? allEdges
                                      .filter((e) => e.from === node.id)
                                      .filter((e) => matchFilters(e.edgeType?.condition, searchQuery.edge))
                                : [];

                        const keyNamesRelevantForFiltering = Object.keys(searchQuery).filter(
                            (key) => key !== "searchType" && key !== "plainQuery",
                        );

                        const displayKeyNamesRelevantForFiltering: string[] = keyNamesRelevantForFiltering.map(
                            (name) => displayNames[name],
                        );

                        groups = keyNamesRelevantForFiltering
                            .map((key) => findFieldsUsingSelectorWithName(key, searchQuery[key], node))
                            .flat()
                            .concat(edges.length ? "edge" : null)
                            .map((name) => displayNames[name])
                            .filter(Boolean);

                        const allChecksPassed = isEqual(groups, displayKeyNamesRelevantForFiltering);

                        const hasValidQueryOrGroups = searchQuery.plainQuery === "" ? false : groupsAux.length === 0;

                        if (!allChecksPassed || hasValidQueryOrGroups) {
                            edges = [];
                            groups = [];
                        }

                        return { node, edges, groups };
                    }
                })
                .filter(({ groups }) => groups.length),
        [displayNames, allEdges, searchQuery, allNodes],
    );
}

export function resolveSearchQuery(text: string): SearchQuery {
    const result: SearchQuery = {};
    const regex = /(\w+):\(([^)]*)\)/g;
    let match: RegExpExecArray | null;
    let lastIndex = 0;

    while ((match = regex.exec(text)) !== null) {
        const key = match[1] as keyof Omit<SearchQuery, "plainQuery">;
        const values = match[2].split(",").map((value) => value.trim().replace(/"/g, ""));
        result[key] = values.length > 0 ? values : [];
        lastIndex = regex.lastIndex;
    }

    result.plainQuery = text.slice(lastIndex).trim();

    return result;
}

export function searchQueryToString(query: SearchQuery): string {
    const plainQuery = query.plainQuery;

    const formattedParts = Object.entries(query)
        .filter(([key]) => key !== "plainQuery")
        .map(([key, value]) => {
            if (Array.isArray(value) && !(value.length === 1 && value[0] === "")) {
                return `${key}:(${value})`;
            } else if (typeof value === "string" && value.length > 0) {
                return `${key}:(${[value]})`;
            }
            return []; // Skip undefined or invalid values
        })
        .filter(Boolean) // Remove null values
        .join(" "); // Join the formatted parts with a space

    // Append plainQuery at the end without a key if it exists
    return plainQuery
        ? `${formattedParts} ${plainQuery}`.trim() // Ensure no trailing space
        : formattedParts;
}
