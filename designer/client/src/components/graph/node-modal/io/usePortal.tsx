import React, { PropsWithChildren, useCallback, useRef } from "react";
import { createPortal } from "react-dom";

export function usePortal(): [React.ComponentType<PropsWithChildren>, React.Ref<HTMLDivElement>] {
    const portalRef = useRef();
    const PortalWrapper = useCallback(({ children }: PropsWithChildren) => {
        if (!portalRef.current) return null;
        return createPortal(children, portalRef.current);
    }, []);
    return [PortalWrapper, portalRef];
}
