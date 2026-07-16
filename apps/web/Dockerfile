FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# The header "Sign in" link is baked into the client bundle at build time.
# Defaults to the production admin domain; overridable via a build arg.
ARG NEXT_PUBLIC_APP_URL=https://app.cerebrozen.in
ARG NEXT_PUBLIC_ADMIN_URL=https://admin.cerebrozen.in
ENV NEXT_PUBLIC_APP_URL=$NEXT_PUBLIC_APP_URL
ENV NEXT_PUBLIC_ADMIN_URL=$NEXT_PUBLIC_ADMIN_URL
RUN npm run build
EXPOSE 3000
CMD ["npm", "run", "start", "--", "-p", "3000", "-H", "0.0.0.0"]
