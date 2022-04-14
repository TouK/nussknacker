import React, { createContext, PropsWithChildren, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState } from "react";
import { __, CurriedFunction1, CurriedFunction2, curry, isArray, pickBy } from "lodash";
import { useSearchParams } from "react-router-dom";
import { useDebouncedValue } from "rooks";

function serializeToQuery<T>(filterModel: T): [string, string][] {
    return Object.entries(filterModel)
        .flatMap(([key, value]) => (isArray(value) ? value.map((v: string) => ({ key, value: v })) : { key, value }))
        .map(({ key, value }) => [key, value]);
}

function deserializeFromQuery<T extends Record<Uppercase<string>, any>>(params: URLSearchParams): T {
    return [...params].reduce((result, [key, _value]) => {
        const value = _value === "true" || (_value !== "false" && _value);
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

interface GetFilter<M> {
    <I extends keyof M, V extends M[I]>(id: I, ensureArray: true): EnsureArray<V>;

    <I extends keyof M, V extends M[I]>(id: I, ensureArray?: false): V;
}

interface FilterSetter<M> {
    <I extends keyof M, V extends M[I]>(id: I, value: V): void;
}

interface SetFilter<M> extends FilterSetter<M> {
    <I extends keyof M, V extends M[I]>(): CurriedFunction2<I, V, void>;

    <I extends keyof M, V extends M[I]>(id: I): CurriedFunction1<V, void>;

    <I extends keyof M, V extends M[I]>(id: __, value: V): CurriedFunction1<I, void>;
}

interface FiltersContextType<M> {
    getFilter: GetFilter<M>;
    setFilter: SetFilter<M>;
    activeKeys: Array<keyof M>;
}

const FiltersContext = createContext<FiltersContextType<any>>(null);

export function useFilterContext<M = unknown>(): FiltersContextType<M> {
    const context = useContext(FiltersContext);
    if (!context) {
        throw "FiltersContext not initialized!";
    }
    return context;
}

interface Props<M> {
    getValueLinker?: (setNewValue: FilterSetter<M>) => FilterSetter<M>;
}

export function FiltersContextProvider<M>({ children, getValueLinker }: PropsWithChildren<Props<M>>): JSX.Element {
    const [searchParams, setSearchParams] = useSearchParams();
    const [model, setModel] = useState<M>(deserializeFromQuery(searchParams));
    const [debouncedModel] = useDebouncedValue(model, 250, { initializeWithNull: true });

    useEffect(() => {
        setModel(deserializeFromQuery(searchParams));
    }, [searchParams]);

    useLayoutEffect(() => {
        debouncedModel && setSearchParams(serializeToQuery(debouncedModel), { replace: true });
    }, [debouncedModel, setSearchParams]);

    const setNewValue = useCallback<FilterSetter<M>>((id, value) => {
        setModel(
            (model) =>
                pickBy(
                    {
                        ...model,
                        [id]: value,
                    },
                    (value) => (isArray(value) ? value.length : !!value),
                ) as unknown as M,
        );
    }, []);

    const setConnectedValue = useMemo(() => getValueLinker?.(setNewValue), [getValueLinker, setNewValue]);

    const setFilter = useCallback<FilterSetter<M>>(
        (id, value) => {
            setNewValue(id, value);
            setConnectedValue?.(id, value);
        },
        [setConnectedValue, setNewValue],
    );

    const getFilter = useCallback<GetFilter<M>>(
        (field, forceArray) => {
            const value = model[field];
            return forceArray ? ensureArray(value) : value;
        },
        [model],
    );

    const ctx = useMemo<FiltersContextType<M>>(
        () => ({
            getFilter,
            setFilter: curry(setFilter),
            activeKeys: Object.keys(debouncedModel || {}) as Array<keyof M>,
        }),
        [debouncedModel, getFilter, setFilter],
    );

    return <FiltersContext.Provider value={ctx}>{children}</FiltersContext.Provider>;
}
