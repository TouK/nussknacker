import { startsWith } from "lodash";
import type { PropsWithChildren } from "react";
import React, { createContext, useCallback, useContext } from "react";

const PathsToMarkContext = createContext<string[] | null>(null);

export const PathsToMarkProvider = ({ value = [], children }: PropsWithChildren<{ value?: string[] }>): React.JSX.Element => {
    return <PathsToMarkContext.Provider value={value}>{children}</PathsToMarkContext.Provider>;
};

export function useDiffMark(): [(path?: string) => boolean, boolean] {
    const pathsToMark = useContext(PathsToMarkContext);
    const callback = useCallback(
        (path = ""): boolean => {
            return pathsToMark?.some((toMark) => startsWith(toMark, path));
        },
        [pathsToMark],
    );
    return [callback, !!pathsToMark];
}
