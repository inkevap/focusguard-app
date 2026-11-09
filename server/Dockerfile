# Se construye desde la raíz del repositorio: `docker compose build` (ver docker-compose.yml).
FROM node:22-slim AS build
WORKDIR /app
COPY server/package*.json ./
RUN npm ci
COPY server/tsconfig*.json ./
COPY server/src ./src
RUN npm run build && npm prune --omit=dev

FROM node:22-slim
WORKDIR /app
ENV NODE_ENV=production \
    SEED_DIR=/app/data/seed \
    OWN_DIR=/app/data/own \
    THEME_DIR=/app/data/theme
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY server/package.json ./
# Tablas del repositorio (categorías e identificadores de vistas) y listas propias que el servidor publica.
COPY data/seed ./data/seed
# Paleta compartida con la app (correos y páginas).
COPY data/theme ./data/theme
COPY server/data ./data/own
USER node
EXPOSE 3000
CMD ["node", "dist/index.js"]
