#!/system/bin/sh
# Physical Volume+ toggles Spotify and Helios on release; Volume- stays volume down.
PIDFILE=/data/local/tmp/helios-volume-home.pid
if [ -f "$PIDFILE" ]; then
    oldpid=$(cat "$PIDFILE")
    # PIDs are reused after reboot; an unrelated process is not our running service.
    if [ -n "$oldpid" ] && kill -0 "$oldpid" 2>/dev/null &&
       tr '\000' ' ' < "/proc/$oldpid/cmdline" 2>/dev/null | grep -q '/data/adb/service.d/helios-volume-home.sh'; then exit 0; fi
fi
echo $$ > "$PIDFILE"
trap 'rm -f "$PIDFILE"' EXIT
while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
INPUT=
for node in /sys/class/input/event*/device/name; do
    read -r name < "$node"
    if [ "$name" = "odm:gpio-keys" ]; then INPUT=/dev/input/$(basename "$(dirname "$(dirname "$node")")"); break; fi
done
[ -n "$INPUT" ] || exit 1
getevent -l "$INPUT" | while IFS= read -r event; do
    case "$event" in
        *KEY_VOLUMEUP*DOWN*) armed=1 ;;
        *KEY_VOLUMEUP*UP*)
            if [ "$armed" = "1" ]; then
                armed=0
                foreground=$(dumpsys activity activities | grep mResumedActivity)
                case "$foreground" in
                    *com.spotify.music*) am start --user 0 -n pl.mateusz.helios/.MainActivity >/dev/null 2>&1 ;;
                    *) am start --user 0 -n com.spotify.music/.MainActivity >/dev/null 2>&1 ;;
                esac
            fi
            ;;
    esac
done
