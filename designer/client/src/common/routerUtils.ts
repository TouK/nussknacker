import { UNSAFE_RouteContext, useLocation, useParams } from "react-router-dom";
import { useContext, useMemo } from "react";

export const useDecodedParams: typeof useParams = () => {
    const location = useLocation();
    const { matches } = useContext(UNSAFE_RouteContext);
    const match = matches[matches.length - 1];
    return useMemo(() => {
        const pathParts = location.pathname.replace(/^\//, "").replace(/\/$/, "").split("/");
        const templateParts = match.route.path.replace(/^\//, "").replace(/\/$/, "").split("/");
        return Object.fromEntries(
            templateParts
                .map((v, index) => {
                    if (v.startsWith(":")) return [v.substring(1), decodeURIComponent(pathParts[index])];
                    if (v === "*") return [v, pathParts.slice(index).join("/")];
                })
                .filter(Boolean),
        );
    }, [location.pathname, match.route.path]);
};
