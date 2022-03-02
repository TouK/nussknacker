import { FiltersModel } from "./filterRules";
import React, { createContext, PropsWithChildren, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState } from "react";
import { __, CurriedFunction1, CurriedFunction2, curry, isArray, pickBy } from "lodash";
import { useSearchParams } from "react-router-dom";
import { useDebouncedValue } from "rooks";

export function serializeToQuery<T>(filterModel: T): [string, string][] {
    return Object.entries(filterModel)
        .flatMap(([key, value]) => (isArray(value) ? value.map((v: string) => ({ key, value: v })) : { key, value }))
        .map(({ key, value }) => [key, value]);
}

export function deserializeFromQuery<T extends Record<Uppercase<string>, any>>(params: URLSearchParams): T {
    return [...params].reduce((result, [key, _value]) => {
        const value = _value === "true" || _value;
        return {
            ...result,
            [key]: result[key] && result[key] !== value ? [].concat(result[key]).concat(value) : value,
        };
    }, {} as any);
}

function ensureArray<T>(value: T | T[]): T[] {
    return value ? [].concat(value) : [];
}

type EnsureArray<V> = V extends Array<any> ? V : V[];

interface GetFilter<M = FiltersModel> {
    <I extends keyof M, V extends M[I]>(id: I, ensureArray: true): EnsureArray<V>;

    <I extends keyof M, V extends M[I]>(id: I, ensureArray?: false): V;
}

interface FilterSetter<M = FiltersModel> {
    <I extends keyof M, V extends M[I]>(id: I, value: V): void;
}

interface SetFilter<M = FiltersModel> extends FilterSetter<M> {
    <I extends keyof M, V extends M[I]>(): CurriedFunction2<I, V, void>;

    <I extends keyof M, V extends M[I]>(id: I): CurriedFunction1<V, void>;

    <I extends keyof M, V extends M[I]>(id: __, value: V): CurriedFunction1<I, void>;
}

interface FiltersContextType<M = FiltersModel> {
    model: M;
    getFilter: GetFilter<M>;
    setFilter: SetFilter<M>;
}

const FiltersContext = createContext<FiltersContextType>(null);

export function useFilterContext(): FiltersContextType {
    const context = useContext(FiltersContext);
    if (!context) {
        throw "FiltersContext not initialized!";
    }
    return context;
}

export function FiltersContextProvider({ children }: PropsWithChildren<unknown>): JSX.Element {
    const [searchParams, setSearchParams] = useSearchParams();
    const [model, setModel] = useState<FiltersModel>(deserializeFromQuery(searchParams));
    const [debouncedModel] = useDebouncedValue(model, 250, { initializeWithNull: true });

    useEffect(() => {
        setModel(deserializeFromQuery(searchParams));
    }, [searchParams]);

    useLayoutEffect(() => {
        debouncedModel && setSearchParams(serializeToQuery(debouncedModel), { replace: true });
    }, [debouncedModel, setSearchParams]);

    const setNewValue = useCallback<FilterSetter<FiltersModel>>((id, value) => {
        setModel((model) =>
            pickBy(
                {
                    ...model,
                    [id]: value,
                },
                (value) => (isArray(value) ? value.length : !!value),
            ),
        );
    }, []);

    const setConnectedValue = useCallback<FilterSetter<FiltersModel>>(
        (id, value) => {
            switch (id) {
                case "USED_ONLY":
                    return value && setNewValue("UNUSED_ONLY", false);
                case "UNUSED_ONLY":
                    return value && setNewValue("USED_ONLY", false);
            }
        },
        [setNewValue],
    );

    const setFilter = useCallback<FilterSetter<FiltersModel>>(
        (id, value) => {
            setNewValue(id, value);
            setConnectedValue(id, value);
        },
        [setConnectedValue, setNewValue],
    );

    const getFilter = useCallback<GetFilter<FiltersModel>>(
        (field, forceArray) => {
            const value = model[field];
            return forceArray ? ensureArray(value) : value;
        },
        [model],
    );

    const ctx = useMemo<FiltersContextType>(
        () => ({
            model: debouncedModel || {},
            getFilter,
            setFilter: curry(setFilter),
        }),
        [getFilter, debouncedModel, setFilter],
    );

    return <FiltersContext.Provider value={ctx}>{children}</FiltersContext.Provider>;
}
