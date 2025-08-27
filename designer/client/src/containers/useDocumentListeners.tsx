import { useEffect, useMemo } from "react";

type Listener<K extends keyof DocumentEventMap> = (event: DocumentEventMap[K]) => void;
export type DocumentListeners = {
    [K in keyof DocumentEventMap]?: Listener<K> | false;
};

const filterEventsByContainer =
    (container: HTMLElement) =>
    <K extends keyof DocumentEventMap>(listener: Listener<K>): Listener<K> =>
    (event) => {
        const path = event.composedPath() || [];
        const rootElements = [window, document, document.documentElement, document.body];
        const parents = path.filter((t) => ![event.target, ...rootElements].includes(t));
        // allow events from root
        // ignore all events from outside of container (modals, overlays, etc)
        if (!parents.length || parents.includes(container)) {
            return listener(event);
        }
    };

export function useDocumentListeners(listeners: DocumentListeners, rootEl: EventTarget = document): void {
    const filterByRoot = useMemo(() => filterEventsByContainer(document.getElementById("root")), []);
    useEffect(() => {
        const entries = Object.entries(listeners).map(([type, listener]) => {
            if (listener) {
                const wrapped = filterByRoot(listener);
                rootEl.addEventListener(type, wrapped);
                return () => rootEl.removeEventListener(type, wrapped);
            }
        });
        return () => entries.forEach((unbind) => unbind());
    }, [filterByRoot, listeners, rootEl]);
}
