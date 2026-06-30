FROM mcr.microsoft.com/playwright:v1.48.0-jammy

ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

WORKDIR /e2e
COPY package.json ./
RUN npm install
COPY . .

CMD ["sh", "-lc", "node wait.js && npx playwright test"]
