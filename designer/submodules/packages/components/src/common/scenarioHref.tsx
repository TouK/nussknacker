import { BASE_ORIGIN, BASE_PATH } from "nussknackerUi/config";
import urljoin from "url-join";

export const BASE_HREF =
    process.env.NODE_ENV !== "production" && BASE_PATH.startsWith(process.env.PROXY_PATH)
        ? process.env.NU_FE_CORE_URL
        : urljoin(BASE_ORIGIN, BASE_PATH);

export function makeRelative(href: string): string {
    return href.replace(BASE_HREF, (match, offset) => (offset > 0 ? match : "/"));
}

function nuHref(path: string, scenarioId: string): string {
    // , and / allowed in scenarioId
    const sid = encodeURIComponent(scenarioId);
    return urljoin(BASE_HREF, path, sid);
}

export function scenarioHref(scenarioId: string): string {
    return nuHref("visualization", scenarioId);
}

function scenarioWithNodesHref(scenarioId: string, nodeIds: string[]): string {
    // , and / allowed in nodeId
    const ids = nodeIds.map((nodeId: string): string => encodeURIComponent(encodeURIComponent(nodeId)));
    return urljoin(scenarioHref(scenarioId), `?nodeId=${ids.join(",")}`);
}

export function nodeHref(scenarioId: string, nodeId: string): string {
    return scenarioWithNodesHref(scenarioId, [nodeId]);
}

export function fragmentNodeHref(scenarioId: string, fragmentNodeId: string, nodeId: string): string {
    return scenarioWithNodesHref(scenarioId, [fragmentNodeId, `${fragmentNodeId}-${nodeId}`]);
}

export function metricsHref(scenarioId: string): string {
    return nuHref("metrics", scenarioId);
}
