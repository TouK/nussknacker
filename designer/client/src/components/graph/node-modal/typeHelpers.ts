type Primitive = string | number | boolean | symbol | null | undefined;

type NormalizePath<P extends string> = P extends `${infer Left}[${infer Index}].${infer Right}`
    ? NormalizePath<`${Left}.${Index}.${Right}`>
    : P;

type ArrayKey = number | `${number}`;
type PathImpl<K extends string | number, V> = V extends Primitive
    ? `${K}`
    : V extends ReadonlyArray<infer U>
    ? `${K}` | `${K}[${ArrayKey}]` | `${K}[${ArrayKey}].${PathImpl<Extract<ArrayKey, string>, U>}` | `${K}.${PathImpl<ArrayKey, U>}`
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
