// https://dev.to/pffigueiredo/typescript-utility-keyof-nested-object-2pa3
import { omit as _omit, pick as _pick } from "lodash";

export type NestedKeyOf<ObjectType extends NonNullable<unknown>> = {
    [Key in keyof ObjectType & (string | number)]: ObjectType[Key] extends NonNullable<unknown>
        ? `${Key}` | `${Key}.${NestedKeyOf<ObjectType[Key]>}`
        : `${Key}`;
}[keyof ObjectType & (string | number)];

export const pick = <T extends NonNullable<unknown>>(object: T, props: NestedKeyOf<T>[]) => _pick(object, props);
export const omit = <T extends NonNullable<unknown>>(object: T, props: NestedKeyOf<T>[]) => _omit(object, props);
