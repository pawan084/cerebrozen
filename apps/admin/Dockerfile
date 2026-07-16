FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# NEXT_PUBLIC_* is inlined into the client bundle at BUILD time — a runtime
# environment: entry in compose never reaches the browser. Set it here.
ARG NEXT_PUBLIC_API_URL=http://localhost:8100
ARG NEXT_PUBLIC_ENGINE_URL=http://localhost:8000
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_ENGINE_URL=$NEXT_PUBLIC_ENGINE_URL
# Where the wordmark and the login footer link back to.
ARG NEXT_PUBLIC_SITE_URL=https://cerebrozen.in
ENV NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL
RUN npm run build
EXPOSE 3000
CMD ["npm", "run", "start", "--", "-p", "3000", "-H", "0.0.0.0"]
