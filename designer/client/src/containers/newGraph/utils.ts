import React, { useContext, useEffect } from "react";

export function createContextHook<T>(Context: React.Context<T>, Provider: React.ComponentType): () => T;
export function createContextHook<T, C>(Context: React.Context<T>, Provider: React.ComponentType, extendFn: (ctx: T) => C): () => C;
export function createContextHook<T, C>(Context: React.Context<T>, Provider: React.ComponentType, extendFn?: (ctx: T) => C) {
    return () => {
        const context = useContext(Context);

        if (!context) throw new Error(`used outside ${Provider.name}`);

        if (typeof extendFn === "function") {
            return extendFn(context);
        }

        return context;
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
