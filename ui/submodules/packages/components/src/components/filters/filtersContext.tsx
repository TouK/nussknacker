import { FiltersModel } from "./filterRules";
import React, { createContext, PropsWithChildren, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useState } from "react";
import { __, CurriedFunction1, CurriedFunction2, curry, isArray, pickBy } from "lodash";
import { useSearchParams } from "react-router-dom";
import { useDebouncedValue } from "rooks";

function serializeToQuery(filterModel: FiltersModel): [string, string][] {
    return Object.entries(filterModel)
        .flatMap(([key, value]) => (isArray(value) ? value.map((v: string) => ({ key, value: v })) : { key, value }))
        .map(({ key, value }) => [key, value]);
}

function deserializeFromQuery(params: URLSearchParams): FiltersModel {
    return [...params].reduce((result, [key, _value]) => {
        const value = _value === "true" || _value;
        return {
            ...result,
            [key]: result[key] && result[key] !== value ? [].concat(result[key]).concat(value) : value,
        };
    }, {});
}

function ensureArray<T>(value: T | T[]): T[] {
    return value ? [].concat(value) : [];
}

type EnsureArray<V> = V extends Array<any> ? V : V[];

interface GetFilter<M = FiltersModel> {
    <I extends keyof M, V extends M[I]>(id: I, ensureArray: true): EnsureArray<V>;

    <I extends keyof M, V extends M[I]>(id: I, ensureArray?: false): V;
}

interface SetFilter<M = FiltersModel> {
    <I extends keyof M, V extends M[I]>(): CurriedFunction2<I, V, void>;

    <I extends keyof M, V extends M[I]>(id: I): CurriedFunction1<V, void>;

    <I extends keyof M, V extends M[I]>(id: __, value: V): CurriedFunction1<I, void>;

    <I extends keyof M, V extends M[I]>(id: I, value: V): void;
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
    const [model, setModel] = useState<FiltersModel>({});
    const [searchParams, setSearchParams] = useSearchParams();
    const [debouncedModel] = useDebouncedValue(model, 250);

    useEffect(() => {
        setModel(deserializeFromQuery(searchParams));
    }, [searchParams]);

    useLayoutEffect(() => {
        setSearchParams(serializeToQuery(debouncedModel), { replace: true });
    }, [debouncedModel, setSearchParams]);

    const setFilter = useCallback<SetFilter>(
        curry((id, value) =>
            setModel((model) =>
                pickBy(
                    {
                        ...model,
                        [id]: value,
                    },
                    (value) => (isArray(value) ? value.length : !!value),
                ),
            ),
        ),
        [],
    );

    const getFilter = useCallback<GetFilter>(
        (field, forceArray) => {
            const value = model[field];
            return forceArray ? ensureArray(value) : value;
        },
        [model],
    );

    const ctx = useMemo<FiltersContextType>(
        () => ({
            model,
            getFilter,
            setFilter,
        }),
        [getFilter, model, setFilter],
    );

    return <FiltersContext.Provider value={ctx}>{children}</FiltersContext.Provider>;
}
