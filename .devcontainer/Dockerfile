# Środowisko deweloperskie: JDK 21 (Temurin) + Node.js 22 + Maven + Angular CLI
FROM eclipse-temurin:21-jdk-noble

ARG NODE_MAJOR=22
ARG USERNAME=dev
ARG USER_UID=1000
ARG USER_GID=1000

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl gnupg git unzip maven \
    && curl -fsSL https://deb.nodesource.com/setup_${NODE_MAJOR}.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g @angular/cli \
    && rm -rf /var/lib/apt/lists/*

# Użytkownik nie-root (UID zgodny z hostem, żeby pliki w bind-mouncie miały właściwego właściciela)
RUN (getent group ${USER_GID} || groupadd --gid ${USER_GID} ${USERNAME}) \
    && (id -u ${USER_UID} >/dev/null 2>&1 && usermod -l ${USERNAME} -d /home/${USERNAME} -m $(id -nu ${USER_UID}) \
        || useradd --uid ${USER_UID} --gid ${USER_GID} -m ${USERNAME})

RUN mkdir -p /home/${USERNAME}/.m2 /home/${USERNAME}/.npm && chown -R ${USER_UID}:${USER_GID} /home/${USERNAME}

ENV NG_CLI_ANALYTICS=false

USER ${USERNAME}
WORKDIR /workspace
