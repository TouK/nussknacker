type Primitive = string | number | boolean | symbol | null | undefined;

export type NormalizePath<P extends string> = P extends `${infer Left}[${infer Index}]${infer Rest}`
    ? Index extends ""
        ? never
        : NormalizePath<`${Left}.${Index}${Rest}`>
    : P;

type ArrayKey<K extends string> = K extends `${infer Key}.${infer I}`
    ?
          | `${Key}[]` // for editor completion only
          | `${Key}[${I}]`
          | `${Key}.#` // for editor completion only
          | `${Key}.${I}`
    : never;

type PathImpl<K extends string | number, V> = V extends Primitive
    ? `${K}`
    : V extends ReadonlyArray<infer U>
    ? `${K}` | ArrayKey<`${K}.${number}`> | `${ArrayKey<`${K}.${number}`>}.${Paths<U>}`
    : `${K}` | `${K}.${Paths<V>}`;

export type Paths<T> = {
    [K in keyof T & (string | number)]: PathImpl<K, T[K]>;
}[keyof T & (string | number)];

type _PathValue<T, P extends string> = P extends `${infer Key}.${infer Rest}`
    ? Key extends keyof T
        ? _PathValue<T[Key], Rest>
        : Key extends `${number}`
        ? T extends ReadonlyArray<infer U>
            ? _PathValue<U, Rest>
            : never
        : never
    : P extends keyof T
    ? T[P]
    : P extends `${number}`
    ? T extends ReadonlyArray<infer U>
        ? U
        : never
    : never;

export type PathValue<T, P extends string> = _PathValue<T, NormalizePath<P>>;

export function normalizePathString<P extends string>(path: P): NormalizePath<P> {
    if (path.match(/\[]./) || path.match(/\.\[\d+]/g) || path.includes(".#")) {
        throw "Invalid path: " + path;
    }
    return path.replace(/\[(\d+)]/g, ".$1").replace(/\[]$/g, "") as NormalizePath<P>;
}
