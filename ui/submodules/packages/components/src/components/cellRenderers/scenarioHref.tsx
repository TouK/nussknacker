import { BASE_ORIGIN, BASE_PATH } from "nussknackerUi/config";

export const urljoin = (...parts: string[]) =>
    parts
        .map((p) => p.trim())
        .join("/")
        .replace(/(?<!:)(\/)+/g, "/");

export function scenarioHref(id: string): string {
    return urljoin(BASE_ORIGIN, BASE_PATH, "/visualization", id);
}
