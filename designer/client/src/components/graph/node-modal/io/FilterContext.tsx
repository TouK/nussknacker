import type { PropsWithChildren } from "react";
import React, { createContext, useCallback, useContext, useState } from "react";

const FilterContext = createContext<{
    onToggle: (id: string) => void;
    filter: string[];
}>(null);

export function useFilterContext() {
    const ctx = useContext(FilterContext);
    if (!ctx) {
        throw new Error("used outside FilterProvider");
    }
    return ctx;
}

export function FilterProvider(props: PropsWithChildren) {
    const [filter, setFilter] = useState<(string | null)[]>([]);
    const onToggle = useCallback(
        (id: string) => setFilter((current) => (current.includes(id) ? current.filter((v) => v !== id) : [...current, id])),
        [],
    );
    return <FilterContext.Provider value={{ filter, onToggle }}>{props.children}</FilterContext.Provider>;
}
