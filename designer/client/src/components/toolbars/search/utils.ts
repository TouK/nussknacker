import { Edge, NodeType } from "../../../types";
import { uniq } from "lodash";
import { useSelector } from "react-redux";
import { getScenario } from "../../../reducers/selectors/graph";
import NodeUtils from "../../graph/NodeUtils";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { ensureArray } from "../../../common/arrayUtils";
import { AdvancedSearch, SearchOption, SearchType, SimpleSearch } from "./SearchResults";

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

function arraysEqual(arr1: string[], arr2: string[]): boolean {
    if (arr1.length !== arr2.length) {
        return false;
    }
    return arr1.slice().sort().join() === arr2.slice().sort().join();
}

export function useFilteredNodes(searchOption: SearchOption): {
    groups: string[];
    node: NodeType;
    edges: Edge[];
}[] {
    console.log(searchOption);
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
                    let edges: Edge[] = [];
                    let groups: string[] = [];

                    switch (searchOption.searchType) {
                        case SearchType.SIMPLE: {
                            edges = allEdges
                                .filter((e) => e.from === node.id)
                                .filter((e) => matchFilters(e.edgeType?.condition, [searchOption.query]));

                            groups = findFields([searchOption.query], node)
                                .concat(edges.length ? "edgeExpression" : null)
                                .map((name) => displayNames[name])
                                .filter(Boolean);

                            console.log(groups);

                            return { node, edges, groups };
                        }
                        case SearchType.ADVANCED: {
                            edges =
                                "edgeExpression" in searchOption
                                    ? allEdges
                                          .filter((e) => e.from === node.id)
                                          .filter((e) => matchFilters(e.edgeType?.condition, searchOption.edgeExpression))
                                    : [];

                            const keyNamesRelevantForFiltering = Object.keys(searchOption).filter((key) => key !== "searchType");

                            const displayKeyNamesRelevantForFiltering: string[] = keyNamesRelevantForFiltering.map(
                                (name) => displayNames[name],
                            );

                            groups = keyNamesRelevantForFiltering
                                .map((key) => findFieldsUsingSelectorWithName(key, searchOption[key], node))
                                .flat()
                                .concat(edges.length ? "edgeExpression" : null)
                                .map((name) => displayNames[name])
                                .filter(Boolean);

                            const allChecksPassed = arraysEqual(groups, displayKeyNamesRelevantForFiltering);

                            if (!allChecksPassed) {
                                edges = [];
                                groups = [];
                            }

                            return { node, edges, groups };
                        }
                    }
                })
                .filter(({ groups }) => groups.length),
        [displayNames, allEdges, searchOption, allNodes],
    );
}

export function resolveSearchOption(filterRawText: string): SearchOption {
    const advancedFilterKeyNames: string[] = [
        "id:(",
        "description:(",
        "type:(",
        "paramName:(",
        "paramValue:(",
        "outputValue:(",
        "edgeExpression:(",
    ]; //heuristic, but sufficient
    const isAdvancedSearchOption = advancedFilterKeyNames.some((advancedKeyName) => filterRawText.includes(advancedKeyName));

    if (isAdvancedSearchOption) {
        return parseRawTextToAdvancedSearch(filterRawText);
    } else {
        return { searchType: SearchType.SIMPLE, query: filterRawText } as SimpleSearch;
    }
}

function parseRawTextToAdvancedSearch(text: string): AdvancedSearch {
    const result: AdvancedSearch = { searchType: SearchType.ADVANCED };
    const regex = /(\w+):\(([^)]*)\)/g;
    let match: RegExpExecArray | null;

    while ((match = regex.exec(text)) !== null) {
        const key = match[1] as keyof Exclude<keyof AdvancedSearch, "searchType">;
        console.log(key);
        const values = match[2].split(",").map((value) => value.trim().replace(/"/g, ""));

        result[key] = values.length > 0 ? values : undefined;
    }

    return result;
}
