FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

RUN rm -f /etc/apt/sources.list.d/yarn.list \
	&& apt-get update \
	&& apt-get install -y --no-install-recommends maven \
	&& apt-get clean \
	&& rm -rf /var/lib/apt/lists/*

ENV MAVEN_CONFIG=/home/vscode/.m2
