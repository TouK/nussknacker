import { lazy } from "@loadable/component";
import React, { PropsWithChildren, SuspenseProps, useMemo } from "react";
import { LibContextProvider } from "./store";
import { loadComponent, splitUrl } from "./tools";
import { Module, ModuleUrl } from "./types";

export type ExternalModuleProps = Pick<SuspenseProps, "fallback"> &
    PropsWithChildren<{
        url: ModuleUrl;
        buildHash?: string;
    }>;

/**
 * Loads external module (federation) from url. Works as modules context provider.
 * After loading module renders children providing context with module accessible by hooks
 * @param url "url" to module in format `${federatedModuleName}/${exposedModule}@http(...).js`
 * @param fallback Fallback component passed to React.Suspense
 * @param buildHash Optional cache busting
 * @param children
 * @constructor
 */
export function ExternalModule<M extends Module>({ children, url, fallback, buildHash }: ExternalModuleProps): JSX.Element {
    const [, context] = useMemo(() => splitUrl(url), [url]);
    const LoadedLib = useMemo(() => lazy.lib(async () => loadComponent(url, buildHash)), [buildHash, url]);

    return (
        <React.Suspense fallback={fallback}>
            <LoadedLib>
                {(lib: M) => (
                    <LibContextProvider<M> lib={lib} scope={context}>
                        {children}
                    </LibContextProvider>
                )}
            </LoadedLib>
        </React.Suspense>
    );
}
