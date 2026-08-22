FROM mcr.microsoft.com/playwright:v1.48.0-jammy

ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

WORKDIR /e2e
COPY package.json ./
RUN npm install
COPY . .

# --fail-on-flaky-tests, with retries left ON: the retry is kept for what it
# TELLS us, not to rescue the run. A test that fails then passes is reported
# as flaky and the build goes red, which is the difference between knowing a
# test is intermittent and only knowing it is broken (WC-283).
CMD ["sh", "-lc", "node wait.js && npx playwright test --fail-on-flaky-tests"]
