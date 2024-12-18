import { Edge, NodeType } from "../../../types";
import { uniq } from "lodash";
import { useSelector } from "react-redux";
import { isEqual } from "lodash";
import { getScenario } from "../../../reducers/selectors/graph";
import NodeUtils from "../../graph/NodeUtils";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ensureArray } from "../../../common/arrayUtils";
import { SearchQuery } from "./SearchResults";

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
        selector: (node) => [node.expression, node.exprVal, node.value],
    },
    {
        name: "outputValue",
        selector: (node) => [node.outputName, node.output, node.outputVar, node.varName],
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
                    let edges: Edge[] = [];
                    let groups: string[] = [];

                    if (isSimpleSearch) {
                        edges = allEdges
                            .filter((e) => e.from === node.id)
                            .filter((e) => matchFilters(e.edgeType?.condition, [searchQuery.plainQuery]));

                        groups = findFields([searchQuery.plainQuery], node)
                            .concat(edges.length ? "edgeExpression" : null)
                            .map((name) => displayNames[name])
                            .filter(Boolean);

                        return { node, edges, groups };
                    } else {
                        const edgesAux = allEdges
                            .filter((e) => e.from === node.id)
                            .filter((e) => matchFilters(e.edgeType?.condition, [searchQuery.plainQuery]));

                        const groupsAux: string[] = findFields([searchQuery.plainQuery], node)
                            .concat(edgesAux.length ? "edgeExpression" : null)
                            .map((name) => displayNames[name])
                            .filter(Boolean);

                        edges =
                            "edgeExpression" in searchQuery
                                ? allEdges
                                      .filter((e) => e.from === node.id)
                                      .filter((e) => matchFilters(e.edgeType?.condition, searchQuery.edgeExpression))
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
                            .concat(edges.length ? "edgeExpression" : null)
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

export function resolveSearchQuery(filterRawText: string): SearchQuery {
    return parseRawTextToSearchQuery(filterRawText);
}

function splitString(input: string): string[] {
    //split string by comma respecting quoted elements
    //"a,b,c" -> ["a", "b", "c"]
    //"a,\"b,c\",d" -> ["a", "b,c", "d"]
    return input.match(/(".*?"|[^",\s]+)(?=\s*,|\s*$)/g) || [];
}

function parseRawTextToSearchQuery(text: string): SearchQuery {
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
