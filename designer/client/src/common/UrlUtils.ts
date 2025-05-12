import urljoin from "url-join";

import { BACKEND_STATIC_URL } from "../config";

const absoluteIconPatternRegexp = /^((http|https|ftp):\/\/)/;

export function absoluteBePath(relativeOrAbsolutePath: string): string {
    if (!absoluteIconPatternRegexp.test(relativeOrAbsolutePath)) {
        return urljoin(BACKEND_STATIC_URL, relativeOrAbsolutePath);
    } else {
        return relativeOrAbsolutePath;
    }
}
