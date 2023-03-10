import React from "react"
import {SwitchTransition} from "react-transition-group"
import {useTranslation} from "react-i18next"
import {ProcessStateType, ProcessType} from "./types"
import {CssFade} from "../CssFade"
import {Popover} from "react-bootstrap"
import {OverlayTrigger} from "react-bootstrap/lib"
import ProcessStateUtils from "./ProcessStateUtils"
import {css, cx} from "@emotion/css"

interface Props {
  processState?: ProcessStateType,
  isStateLoaded?: boolean,
  process: ProcessType,
  animation?: boolean,
  height?: number,
  width?: number,
  popover?: boolean,
}

const ImagePopover = ({processName, tooltip, errors}: {
  processName: string,
  tooltip: string,
  errors: Array<string>,
}) => {
  const {t} = useTranslation()
  return (
    <Popover
      className={css({
        marginTop: "5px",
        backgroundColor: "#444",
        h3: {
          backgroundColor: "#444",
        },
      })}
      title={processName}
    >
      <strong>{tooltip}</strong>
      {errors.length !== 0 ?
        (
          <div>
            <span>{t("stateIcon.errors", "Errors:")}</span>
            <ul>
              {errors.map((error, key) => <li key={key}>{error}</li>)}
            </ul>
          </div>
        ) :
        null
      }
    </Popover>
  )
}

function ProcessStateIcon(props: Props) {
  const {
    animation = true,
    process,
    processState,
    isStateLoaded,
    height = 24,
    width = 24,
    popover,
  } = props
  const icon = ProcessStateUtils.getStatusIcon(process, processState, isStateLoaded)
  const tooltip = ProcessStateUtils.getStatusTooltip(process, processState, isStateLoaded)
  const errors = (isStateLoaded ? processState?.errors : process?.state?.errors) || []

  const iconClass = cx(
    css({
      filter: "invert() hue-rotate(180deg) saturate(500%) brightness(120%)",
    }),
    !isStateLoaded && css({
      padding: "50%",
    }),
    //TODO: normalize colors svg files
    (process.isArchived || process.isSubprocess) && css({
      filter: "invert()",
      opacity: 1,
    })
  )
  const transitionKey = `${process.id}-${icon}`

  const image = (
    <img
      src={icon}
      alt={tooltip}
      title={tooltip}
      className={iconClass}
      height={height}
      width={width}
    />
  )

  return animation ?
    (
      <SwitchTransition>
        <CssFade key={transitionKey}>
          {popover ?
            (
              <OverlayTrigger
                trigger={["click"]}
                placement={"left"}
                overlay={(
                  <ImagePopover
                    errors={errors}
                    processName={process.name}
                    tooltip={tooltip}
                  />
                )}
              >
                {image}
              </OverlayTrigger>

            ) :
            image}
        </CssFade>
      </SwitchTransition>
    ) :
    image
}

export default ProcessStateIcon

