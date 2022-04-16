import { BASE_ORIGIN, BASE_PATH } from "nussknackerUi/config";
import urljoin from "url-join";

function nuHref(path: string, scenarioId: string): string {
    // , and / allowed in scenarioId
    const sid = encodeURIComponent(scenarioId);
    const root = urljoin(BASE_ORIGIN, BASE_PATH);
    return urljoin(window.location.href.startsWith(root) ? "/" : root, path, sid);
}

export function scenarioHref(scenarioId: string): string {
    return nuHref("visualization", scenarioId);
}

export function nodeHref(scenarioId: string, nodeId: string): string {
    // , and / allowed in nodeId
    const nid = encodeURIComponent(nodeId);
    // double encode because of query arrays and react-router
    return encodeURI(urljoin(scenarioHref(scenarioId), `?nodeId=${nid}`));
}

export function metricsHref(scenarioId: string): string {
    return nuHref("metrics", scenarioId);
}
