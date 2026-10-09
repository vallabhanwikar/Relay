# OpenJML in a disposable, network-less container (design v3, "Formal methods stack").
#
# OpenJML is GPLv2. Relay never links it: the proof module runs this image as a separate
# process and reads its output, which keeps Relay itself Apache-2.0. Re-check that reasoning
# against the licence text before any release.
#
#   docker build -t relay/openjml:21.0.28 sandbox-runner/openjml
#
# The version is pinned on purpose: 21.0.28 fixed an enum axiom that could make proofs succeed
# unsoundly, and every verdict records the exact tool version it came from.

FROM ubuntu:24.04

ARG OPENJML_VERSION=21.0.28

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl unzip \
 && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/openjml.zip \
      "https://github.com/OpenJML/OpenJML/releases/download/${OPENJML_VERSION}/openjml-ubuntu-24.04-${OPENJML_VERSION}.zip" \
 && mkdir -p /opt/openjml \
 && unzip -q /tmp/openjml.zip -d /opt/openjml \
 && rm /tmp/openjml.zip \
 && chmod -R a+rX /opt/openjml \
 && OJ="$(find /opt/openjml -maxdepth 3 -type f -name openjml | head -n 1)" \
 && test -n "$OJ" \
 && chmod a+x "$OJ" \
 && find /opt/openjml -type f -path '*/Solvers*' -exec chmod a+x {} + \
 && ln -s "$OJ" /usr/local/bin/openjml \
 && openjml --version

# Verification runs as an unprivileged user with no network (enforced by `docker run`).
RUN useradd --create-home --shell /usr/sbin/nologin verifier
USER verifier
WORKDIR /work

ENTRYPOINT ["openjml"]
CMD ["--version"]
