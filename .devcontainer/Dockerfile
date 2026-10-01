FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Node.js 22
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Firebase CLI
RUN npm install -g firebase-tools

RUN curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg \
        | gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" \
        > /etc/apt/sources.list.d/google-cloud-sdk.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends google-cloud-cli google-cloud-cli-firestore-emulator \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Create cache directories
RUN mkdir -p /home/vscode/.m2 /home/vscode/.npm \
    && chown -R vscode:vscode /home/vscode/.m2 /home/vscode/.npm

USER vscode
WORKDIR /workspace
