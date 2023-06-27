import { BASE_ORIGIN, BASE_PATH } from "nussknackerUi/config";
import urljoin from "url-join";

export const BASE_HREF = urljoin(BASE_ORIGIN, BASE_PATH);

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

export function nodeHref(scenarioId: string, nodeId: string): string {
    // , and / allowed in nodeId
    const nid = encodeURIComponent(encodeURIComponent(nodeId));
    return urljoin(scenarioHref(scenarioId), `?nodeId=${nid}`);
}

export function fragmentNodeHref(scenarioId: string, fragmentNodeId: string, nodeId: string): string {
    const nid = encodeURIComponent(encodeURIComponent(nodeId));
    const fid = encodeURIComponent(encodeURIComponent(fragmentNodeId));

    return urljoin(scenarioHref(scenarioId), `?nodeId=${fid},${fid}-${nid}`);
}

export function metricsHref(scenarioId: string): string {
    return nuHref("metrics", scenarioId);
}
