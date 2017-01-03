# -*- shell-script -*-

PING_HOME="$(cd $(dirname $(readlink -f $BASH_SOURCE)); pwd)"

# Use the local .m2 directory for maven stuff.
export BOOT_JVM_OPTIONS="-Duser.home=${PING_HOME}"

# Prevent hotspot from optimizing out stack traces for NPE and other exceptions.
export BOOT_JVM_OPTIONS="${BOOT_JVM_OPTIONS} -XX:-OmitStackTraceInFastThrow"

# Tell boot not to create the 'target' directory.
export BOOT_EMIT_TARGET=no

export PATH=/nix/var/nix/profiles/per-app/lb/bin:${PING_HOME}/bin:${PATH}

# Start emacs with bundled prelude configuration.
function p-emacs {
    (export TERM=xterm

     # These are needed to get emacs to ignore the user's real .emacs.d.
     # See: https://www.gnu.org/software/emacs/manual/html_node/emacs/Find-Init.html#Find-Init
     export HOME="$PING_HOME"
     unset USER
     unset LOGNAME

     # Start with nice in case running on a production server.
     nice emacs "$@")
}

alias p="cd $PING_HOME"
alias p-ps="systemctl --no-pager status ping"
alias p-status="p-ps"
alias p-start="sudo systemctl start ping"
alias p-stop="sudo systemctl stop ping"
alias p-restart="sudo systemctl restart ping"
alias p-reload="sudo systemctl reload ping"
alias p-log="journalctl -u ping"
alias p-tail="journalctl -f -u ping"

alias p-sftp-start="sudo systemctl start sftp-heartbeat"
alias p-sftp-status="sudo systemctl status sftp-heartbeat"
alias p-sftp-stop="sudo systemctl stop sftp-heartbeat"

# List outdated dependencies.
alias p-outdated="(cd $PING_HOME; boot -d boot-deps ancient)"
