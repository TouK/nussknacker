import {Moment} from "moment"
import React, {useCallback, useMemo} from "react"
import {DropdownButton} from "../../common/DropdownButton"
import {ButtonWithFocus} from "../../withFocus"

export interface Range {
  name: string,
  from: () => Moment,
  to: () => Moment,
}

interface RangesButtonsProps {
  ranges: Range[],
  onChange: (value: [Moment, Moment]) => void,
  limit?: number,
}

export function CountsRangesButtons({ranges, onChange, limit = 2}: RangesButtonsProps): JSX.Element {
  const changeHandler = useCallback(
    ({from, to}: Range) => onChange([from(), to()]),
    [onChange],
  )

  const visible = useMemo(() => ranges.slice(0, limit), [ranges, limit])
  const collapsed = useMemo(() => ranges.slice(limit), [ranges, limit])

  return (
    <>
      {visible.map(range => (
        <ButtonWithFocus
          key={range.name}
          type="button"
          title={range.name}
          className="predefinedRangeButton"
          onClick={() => changeHandler(range)}
          style={{
            flex: 1,
          }}
        >
          {range.name}
        </ButtonWithFocus>
      ))}

      {collapsed.length > 0 ?
        (
          <DropdownButton
            options={collapsed.map((value) => ({label: value.name, value}))}
            onRangeSelect={changeHandler}
            className="predefinedRangeButton"
            style={{
              flex: 1,
            }}
            wrapperStyle={{
              display: "flex",
              flex: 2,
            }}
          >
            Select more...
          </DropdownButton>
        ) :
        null}
    </>
  )
}
