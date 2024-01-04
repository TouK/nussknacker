import { BASE_ORIGIN, BASE_PATH } from "nussknackerUi/config";
import urljoin from "url-join";

export const BASE_HREF =
    process.env.NODE_ENV !== "production" && BASE_PATH.startsWith(process.env.PROXY_PATH)
        ? process.env.NU_FE_CORE_URL
        : urljoin(BASE_ORIGIN, BASE_PATH);

export function makeRelative(href: string): string {
    return href.replace(BASE_HREF, (match, offset) => (offset > 0 ? match : "/"));
}

function nuHref(path: string, scenarioName: string): string {
    // , and / allowed in scenarioName
    const sid = encodeURIComponent(scenarioName);
    return urljoin(BASE_HREF, path, sid);
}

export function scenarioHref(scenarioName: string): string {
    return nuHref("visualization", scenarioName);
}

function scenarioWithNodesHref(scenarioName: string, nodeIds: string[]): string {
    // , and / allowed in nodeId
    const ids = nodeIds.map((nodeId: string): string => encodeURIComponent(encodeURIComponent(nodeId)));
    return urljoin(scenarioHref(scenarioName), `?nodeId=${ids.join(",")}`);
}

export function nodeHref(scenarioName: string, nodeId: string): string {
    return scenarioWithNodesHref(scenarioName, [nodeId]);
}

export function fragmentNodeHref(scenarioName: string, fragmentNodeId: string, nodeId: string): string {
    return scenarioWithNodesHref(scenarioName, [fragmentNodeId, `${fragmentNodeId}-${nodeId}`]);
}

export function metricsHref(scenarioName: string): string {
    return nuHref("metrics", scenarioName);
}
