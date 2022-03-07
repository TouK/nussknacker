type DataRow = Record<string, any>;

interface FilterRuleFn<D extends DataRow, V = any> {
    (row: D, value: V | null): boolean;
}

type FilterRulesModel<D extends DataRow, M extends Record<string, any>> = {
    [key in keyof M]: FilterRuleFn<D, M[key]>;
};

interface FilterRule<D, M, K extends keyof M = keyof M> {
    rule: FilterRuleFn<D, M[K]>;
    key: K;
}

export type FilterRules<D extends DataRow, M extends Record<string, any>> = FilterRule<D, M>[];

export function createFilterRules<D, M>(rules: FilterRulesModel<D, M>): FilterRules<D, M> {
    return Object.entries(rules || {}).map(([key, rule = () => true]) => ({ key, rule } as FilterRule<D, M>));
}
