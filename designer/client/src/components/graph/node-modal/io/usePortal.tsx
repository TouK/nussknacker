import type { PropsWithChildren } from "react";
import type React from "react";
import { useCallback, useRef } from "react";
import { createPortal } from "react-dom";

export function usePortal(): [React.ComponentType<PropsWithChildren>, React.Ref<HTMLDivElement>] {
    const portalRef = useRef(null);
    const PortalWrapper = useCallback(({ children }: PropsWithChildren) => {
        if (!portalRef.current) return null;
        return createPortal(children, portalRef.current);
    }, []);
    return [PortalWrapper, portalRef];
}
