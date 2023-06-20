import { UNSAFE_RouteContext, useLocation, useParams } from "react-router-dom";
import { useContext, useMemo } from "react";

export const useDecodedParams: typeof useParams = () => {
    const { pathname } = useLocation();
    const { matches } = useContext(UNSAFE_RouteContext);

    const templateParts = useMemo(() => {
        return matches.flatMap((m) => m.route.path?.split("/")).filter(Boolean);
    }, [matches]);

    const pathParts = useMemo(() => {
        return pathname.replace(/^\//, "").replace(/\/$/, "").split("/");
    }, [pathname]);

    return useMemo(() => {
        const paramEntries = templateParts.map((pathPart, index) => {
            if (pathPart.startsWith(":")) return [pathPart.substring(1), decodeURIComponent(pathParts[index])];
            if (pathPart === "*") return [pathPart, pathParts.slice(index).join("/")];
        });
        return Object.fromEntries(paramEntries.filter(Boolean));
    }, [pathParts, templateParts]);
};
