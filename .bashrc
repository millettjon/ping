# -*- shell-script -*-

RIEMANN_PING_HOME="$(cd $(dirname $(readlink -f $BASH_SOURCE)); pwd)"

# Use the local .m2 directory for maven stuff.
export BOOT_JVM_OPTIONS="-Duser.home=${RIEMANN_PING_HOME}"

# Prevent hotspot from optimizing out stack traces for NPE and other exceptions.
export BOOT_JVM_OPTIONS="${BOOT_JVM_OPTIONS} -XX:-OmitStackTraceInFastThrow"

# Tell boot not to create the 'target' directory.
export BOOT_EMIT_TARGET=no

export PATH=/nix/var/nix/profiles/per-app/lb/bin:${RIEMANN_PING_HOME}/bin:${PATH}

# Is boot in nix?
# yes, boot version is 2.2.0, current version is 2.5.5

# Start emacs with bundled prelude configuration.
function e-prelude {
    (export TERM=xterm HOME="$RIEMANN_PING_HOME"; nice emacs "$@")
}
