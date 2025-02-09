# Backend
Backend for the Social Interaction Sensing project.
Open the [api.yaml](api.yaml) for the API specification. This will likely change as the backend gets built.

## Setup
We use Node.js v20.13.1. If you use nvm, you can run `nvm use` to switch to the correct version.

To install all dependencies, run:
```shell
npm i
```

## Deployment

For PM2-deployments, we can use peer-authentication to connect to the database. An example PM2 environment is
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

`DB_USE_ENV=true` will tell `pg` to use the default connection, and `PGHOST=/var/run/postgresql` allows us to utilize
the socket connection.
