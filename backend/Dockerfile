FROM node:26.12.1-slim AS build

WORKDIR /home/node/app

COPY package*.json ./

RUN npm ci

COPY tsconfig.json ./
COPY src ./src

RUN npm run build

# runtime image: production dependencies and compiled code only, run as the
# unprivileged node user
FROM node:26.12.1-slim

ENV NODE_ENV=production

WORKDIR /home/node/app

COPY package*.json ./

RUN npm ci --omit=dev && npm cache clean --force

COPY --from=build /home/node/app/dist ./dist

# mount a volume here; a new named volume takes over the owner of this folder
ENV APP_UPLOAD_LOCATION=/home/node/app/uploads
RUN mkdir uploads && chown node:node uploads

USER node

EXPOSE 3000

CMD ["node", "./dist/index.js"]
