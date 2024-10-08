import React, { useContext, useEffect, useMemo } from "react";

const getComponentName = (Component: React.ComponentType): string => {
    if (Component.displayName) return Component.displayName;
    return (Component["render"] || Component).name;
};

export function createContextHook<T>(Context: React.Context<T>, Provider: React.ComponentType): () => T;
export function createContextHook<T, C>(Context: React.Context<T>, Provider: React.ComponentType, extendFn: (ctx: T) => C): () => C;
export function createContextHook<T, C>(Context: React.Context<T>, Provider: React.ComponentType, extendFn?: (ctx: T) => C) {
    return function () {
        const context = useContext(Context);

        return useMemo(() => {
            if (!context) {
                throw new Error(`context hook used outside ${getComponentName(Provider) || "context provider"}`);
            }
            if (typeof extendFn === "function") {
                return extendFn(context);
            }
            return context;
        }, [context]);
    };
}

export function useContextForward<C>(forwardedRef: React.ForwardedRef<C>, context: C) {
    useEffect(() => {
        if (typeof forwardedRef === "function") {
            forwardedRef(context);
        } else if (forwardedRef) {
            forwardedRef.current = context;
        }
    }, [context, forwardedRef]);
}
