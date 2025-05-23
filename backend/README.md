# Backend

Backend for the Social Interaction Sensing project.
Open the [api.yaml](api.yaml) for the API specification. This will likely change as the backend gets
built.

## Setup

We use Node.js v20.13.1. If you use nvm, you can run `nvm use` to switch to the correct version.

To install all dependencies, run:

```shell
npm i
```

## Deployment

For PM2-deployments, we can use peer-authentication to connect to the database. An example PM2
environment is
provided:

```js
module.exports = {
  apps: [{
    name: "sisensing-backend",
    cwd: "./nodejs",
    script: "./index.js",
    autorestart: true,
    env: {
      "NODE_ENV": "production",
      "APP_PORT": "50000",
      "AUTH_JWT_SECRET": "your secret here please change it or else you'll suffer the consequences again",
      "PGHOST": "/var/run/postgresql",
      "DB_USE_ENV": "true"
    }
  }]
}
```

`DB_USE_ENV=true` will tell `pg` to use the default connection, and `PGHOST=/var/run/postgresql`
allows us to utilize
the socket connection.

## Configuration

### EMA/ESM

#### Adding links to external questionnaires (such as Unipark, LimeSurvey, etc.)

1. Add a new questionnaire to `esm_questionnaires`.
2. Add the desired trigger to `esm_triggers`.
3. Add the element to `esm_elements`:
   a. `type` is `external_questionnaire_link`, other element configuration (position, name) is at
   the discretion of the study
   b. `configuration` is a JSON object with the following keys:
    - `externalUrl`: the URL of the external questionnaire
    - `urlParams`: a list of parameters to be added to the URL as query parameters, e.g.
      `[{"key": "enrolment", "value": "configuration.enrolmentId"}]`
    - `actionName`: the text that will be shown on the button to open the external questionnaire

The following parameters can be defined:

| Parameter                   | Description                                                                                                                                                        |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `configuration.enrolmentId` | The unique ID of the participant                                                                                                                                   |
| `generatedKey.{YOUR_KEY}`   | A generated key that can be used to anonymously identify the same participant across questionnaires. Replace `{YOUR_KEY}` with your preferred key, is not exposed. |

The link to the external questionnaire will be displayed within the default questionnaire interface.
This allows the study author to show explanations and also utilize the questionnaire inbox.
![Screenshot of questionnaire with external link](https://github.com/user-attachments/assets/0642bf46-0290-4d78-8a02-8cb60af71550)

#### Sampling the participant's social network

WHALE provides the possibility to ask participants about their social network. The contact list is
persisted in the database to ensure tracing of the same people across questionnaires.
When saving the questionnaire, only the IDs of the listed people are sent to the server, ensuring
anonymity of non-participants.

Each added person can be rated on a set of questions. To define the questions, a new questionnaire
needs to be created. Any element type can be used (except for `social_network_entry` and
`social_network_rating`).

To get started:
1. Add the network sampling questionnaire:
    - (Optional) Add a new questionnaire to `esm_questionnaires`.
    - Add an element with the type `social_network_entry` to the questionnaire.
    - Add any desired triggers to `esm_triggers`.
2. Add the per-person rating questionnaire:
    - (Optional) Add a new questionnaire to `esm_questionnaires`.
    - Add an element with the type `social_network_rating` to the questionnaire.
    - Add any desired triggers to `esm_triggers`.
3. Add the rating element (type `social_network_rating`) in another questionnaire (at the end of
   day, or end of study, etc.)
    - In the element configuration JSON, set the `ratingQuestionnaireId` to the ID of the per-person
      rating questionnaire.
    - Please note that the backend does not check if the questionnaire ID is valid, so make sure to
      set it correctly.

Here's how it looks like in the questionnaire:

| Element Type            | Image                                                                                              | 
|-------------------------|----------------------------------------------------------------------------------------------------|
| `social_network_entry`  | ![Entry Element](https://github.com/user-attachments/assets/82d0f685-f376-4106-af04-92b898ee54e9)  |
| `social_network_rating` | ![Rating Element](https://github.com/user-attachments/assets/fcde1a38-3dbf-47ec-89b6-216109c25236) |

#### Circumplex model
A circumplex entry can be added to the questionnaire with the element of type `circumplex`.
The configuration is a JSON object with the following keys:
- `imageUrl`: the URL of the image to be displayed, will be pre-loaded on study start to ensure offline access
- `clip`: object that defines the clipping area of the image (only taps inside that area are registered), with the following keys
  - `top`: top offset in pixels
  - `bottom`: bottom offset in pixels
  - `left`: left offset in pixels
  - `right`: right offset in pixels