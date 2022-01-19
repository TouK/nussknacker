import { BASE_ORIGIN, BASE_PATH } from "nussknackerUi/config";

export const urljoin = (...parts: string[]) =>
    parts
        .map((p) => p.trim())
        .join("/")
        .replace(/(?<!:)(\/)+/g, "/");

export function scenarioHref(scenarioId: string): string {
    // , and / allowed in scenarioId
    return urljoin(BASE_ORIGIN, BASE_PATH, "/visualization", encodeURIComponent(scenarioId));
}

export function nodeHref(scenarioId: string, nodeId: string): string {
    // , and / allowed in nodeId
    // double encode because of query arrays and react-router
    return encodeURI(urljoin(scenarioHref(scenarioId), `?nodeId=${encodeURIComponent((nodeId))}`));
}
