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

Adding links to external questionnaires (such as Unipark, LimeSurvey, etc.):

1. Add a new questionnaire to `esm_questionnaires`.
2. Add the desired trigger to `esm_triggers`.
3. Add the element to `esm_elements`:
   a. `type` is `external_questionnaire_link`, other element configuration (position, name) is at the discretion of the study
   b. `configuration` is a JSON object with the following keys:
    - `externalUrl`: the URL of the external questionnaire
    - `urlParams`: a list of parameters to be added to the URL as query parameters, e.g.
      `[{"key": "enrolment", "value": "configuration.enrolmentId"}]`

The following parameters can be defined:

| Parameter                   | Description                                                                                         |
|-----------------------------|-----------------------------------------------------------------------------------------------------|
| `configuration.enrolmentId` | The unique ID of the participant                                                                    |
| `generatedKey.{YOUR_KEY}`   | A generated key that can be used to anonymously identify the same participant across questionnaires |