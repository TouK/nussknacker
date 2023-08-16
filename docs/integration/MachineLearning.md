---
sidebar_position: 5
---

# Machine Learning (Enterprise only)

## Overview
                              
Nussknacker can evaluate ML models using the Machine Learning component. The ML Enricher is an Enterprise component of Nussknacker and requires a separate license, contact info@nussknacker.io for license terms and instructions how to obtain jar with ML Enricher. 

Models can be either JPMML encoded or exported with H2O Mojo/Pojo. 
Model repository can be one of the following:
- file system
- MLFlow registry
- custom model registry
                                 

## Configuration

The Machine Learning enricher is configured under `components` configuration key. Check this [configuration file snippet](../installation_configuration_guide/Common.md#configuration-areas) to understand the placement of `components` configuration key in the configuration file.

Sample configuration:
```
components.prinzPMML {  
    pmmlConfig {
      fileExtension: ".pmml"
      modelsDirectory: "file:///opt/nussknacker/pmml-models"
      modelVersionSeparator: "-v"
    }
  }
```
If ML Enricher is configured, Designer will automatically generate a component for each ML model found and place them in the Enrichers section of the tools palette.

If new ML models are added, Designer needs to be restarted to detect them. 
