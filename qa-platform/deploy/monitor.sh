#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# QA Platform — Live server resource monitor
#
# Refreshes once per second (override with first arg: `bash monitor.sh 2`).
# Press Ctrl+C to exit.
#
# Shows:
#   • CPU, RAM, Swap, Disk — coloured bars + percentages
#   • Container roster — count by health state + per-container CPU/MEM/NetIO
#
# Zero dependencies beyond what ships with Ubuntu — awk, df, free, docker,
# tput, /proc. No Python, no top, no htop required.
# ─────────────────────────────────────────────────────────────────────────────
set -u

# Refresh interval in seconds. Default 0.5 → ~2 visual updates/sec, which
# is the sweet spot for "feels live without being noisy". Bash `sleep`
# accepts fractional values (GNU coreutils on Ubuntu/Debian/Mac), so
# `bash monitor.sh 0.25` or `bash monitor.sh 2` both work.
#
# Note: the actual frame rate is bounded by how long `docker stats
# --no-stream` takes (typically 0.5–1.5s for 10+ containers). Setting
# this below 0.5 won't go faster than the Docker daemon can answer.
INTERVAL="${1:-0.5}"

# ── ANSI ───────────────────────────────────────────────────────────────────
R=$'\033[31m'  # red
Y=$'\033[33m'  # yellow
G=$'\033[32m'  # green
C=$'\033[36m'  # cyan
D=$'\033[2m'   # dim
B=$'\033[1m'   # bold
RST=$'\033[0m'

# Return the color for a percentage: green <50, yellow 50–80, red ≥80
color_for() {
    local p=$1
    [[ $p -ge 80 ]] && { echo "$R"; return; }
    [[ $p -ge 50 ]] && { echo "$Y"; return; }
    echo "$G"
}

# Render a coloured progress bar (default width 40)
bar() {
    local pct=$1 width=${2:-40}
    [[ $pct -gt 100 ]] && pct=100
    [[ $pct -lt 0 ]] && pct=0
    local filled=$(( pct * width / 100 ))
    local empty=$(( width - filled ))
    local clr; clr=$(color_for "$pct")
    printf "%s" "$clr"
    [[ $filled -gt 0 ]] && printf '█%.0s' $(seq 1 $filled)
    printf "%s" "$D"
    [[ $empty -gt 0 ]] && printf '░%.0s' $(seq 1 $empty)
    printf "%s" "$RST"
}

# Cleanup on exit — restore cursor + clear colour state
cleanup() { tput cnorm 2>/dev/null; printf "%s\n" "$RST"; exit 0; }
trap cleanup INT TERM
tput civis 2>/dev/null

# Clear the screen once at startup; the loop below repaints in place so the
# terminal doesn't flicker every refresh. After the first frame we just move
# the cursor home (ESC[H) and overwrite line-by-line, then trim any leftover
# rows below with ESC[J (clear to end of screen).
clear

# CPU sampling — we keep the previous tick around and compute the delta
read_cpu() {
    awk '/^cpu / {
        total = $2+$3+$4+$5+$6+$7+$8+$9
        idle  = $5+$6
        print total, total-idle
    }' /proc/stat
}
read -r CPU_T0 CPU_B0 < <(read_cpu)

# ── Main loop ──────────────────────────────────────────────────────────────
while true; do
    # Cursor to top-left without erasing the screen — the next prints
    # overwrite the previous frame in-place, eliminating the per-tick
    # flash you get with `clear`.
    printf '\033[H'

    # ── Header ────────────────────────────────────────
    NOW=$(date '+%F %T')
    HOST=$(hostname -s)
    UPT=$(uptime -p 2>/dev/null | sed 's/^up //' || echo "?")
    LOAD=$(awk '{printf "%.2f / %.2f / %.2f", $1, $2, $3}' /proc/loadavg)

    # `\033[K` before every `\n` erases from the cursor to end-of-line, so
    # any leftover chars from the previous (longer) frame are wiped. Without
    # this, "CPU 12%" overwrites "CPU 3%" but leaves the trailing "2%" or
    # similar artefacts behind.
    printf "${B}${C}━━━ QA Platform · Live Monitor ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}\033[K\n"
    printf "  ${D}Time:${RST} %s    ${D}Host:${RST} %s    ${D}Up:${RST} %s\033[K\n" "$NOW" "$HOST" "$UPT"
    printf "  ${D}Load:${RST} %s  ${D}(1m / 5m / 15m)${RST}\033[K\n\033[K\n" "$LOAD"

    # ── CPU ───────────────────────────────────────────
    read -r CPU_T1 CPU_B1 < <(read_cpu)
    DT=$(( CPU_T1 - CPU_T0 ))
    DB=$(( CPU_B1 - CPU_B0 ))
    CPU=0
    [[ $DT -gt 0 ]] && CPU=$(( DB * 100 / DT ))
    CPU_T0=$CPU_T1; CPU_B0=$CPU_B1
    CORES=$(nproc)

    # ── Memory + Swap (from /proc/meminfo) ───────────
    eval "$(awk '
        /^MemTotal:/    {t=$2}
        /^MemAvailable:/{a=$2}
        /^SwapTotal:/   {st=$2}
        /^SwapFree:/    {sf=$2}
        END { printf "MU=%d MT=%d SU=%d ST=%d", t-a, t, st-sf, st }
    ' /proc/meminfo)"
    MEM=$(( MT > 0 ? MU * 100 / MT : 0 ))
    SWAP=$(( ST > 0 ? SU * 100 / ST : 0 ))
    MU_H=$(awk "BEGIN{printf \"%.1fG\", $MU/1024/1024}")
    MT_H=$(awk "BEGIN{printf \"%.1fG\", $MT/1024/1024}")
    if [[ $ST -gt 0 ]]; then
        SU_H=$(awk "BEGIN{printf \"%.0fM\", $SU/1024}")
        ST_H=$(awk "BEGIN{printf \"%.1fG\", $ST/1024/1024}")
    else
        SU_H="–"; ST_H="off"
    fi

    # ── Disk (/) ──────────────────────────────────────
    DSK=$(df -h / | awk 'NR==2 {print $3 "|" $2 "|" $5}')
    DU=${DSK%%|*}; rest=${DSK#*|}
    DT_H=${rest%%|*}; DPCS=${rest#*|}
    DPC=${DPCS%\%}

    # ── Print the four bars ───────────────────────────
    printf "  ${B}CPU${RST}    "; bar $CPU
    printf "  %s%3d%%%s  ${D}%d cores${RST}\033[K\n" "$(color_for $CPU)" "$CPU" "$RST" "$CORES"

    printf "  ${B}RAM${RST}    "; bar $MEM
    printf "  %s%3d%%%s  ${D}%s / %s${RST}\033[K\n" "$(color_for $MEM)" "$MEM" "$RST" "$MU_H" "$MT_H"

    printf "  ${B}Swap${RST}   "; bar $SWAP
    printf "  %s%3d%%%s  ${D}%s / %s${RST}\033[K\n" "$(color_for $SWAP)" "$SWAP" "$RST" "$SU_H" "$ST_H"

    printf "  ${B}Disk${RST}   "; bar $DPC
    printf "  %s%3d%%%s  ${D}%s / %s${RST}\033[K\n\033[K\n" "$(color_for $DPC)" "$DPC" "$RST" "$DU" "$DT_H"

    # ── Container roster ──────────────────────────────
    if command -v docker >/dev/null 2>&1; then
        TOTAL=$(docker ps -q 2>/dev/null | wc -l)
        H=$( docker ps --filter health=healthy   -q 2>/dev/null | wc -l)
        UH=$(docker ps --filter health=unhealthy -q 2>/dev/null | wc -l)
        SH=$(docker ps --filter health=starting  -q 2>/dev/null | wc -l)
        NH=$(( TOTAL - H - UH - SH ))

        printf "${B}${C}━━━ Containers (%d) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}\033[K\n" "$TOTAL"
        printf "  healthy: ${G}%d${RST}   starting: ${Y}%d${RST}   unhealthy: ${R}%d${RST}   no-hc: ${D}%d${RST}\033[K\n\033[K\n" \
            "$H" "$SH" "$UH" "$NH"

        printf "  ${B}%-40s %7s %18s %16s${RST}\033[K\n" "NAME" "CPU%" "MEM" "NET I/O"
        printf "  ${D}────────────────────────────────────────────────────────────────────────────────────${RST}\033[K\n"

        # docker stats --no-stream samples once and returns; sort by name for stable rows.
        docker stats --no-stream \
            --format "{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.NetIO}}" 2>/dev/null \
        | sort \
        | while IFS='|' read -r NAME CPU_C MEM_C NET_C; do
            # Color the CPU% column the same way we colour bars
            CPU_VAL=${CPU_C%\%}
            CPU_INT=$(awk "BEGIN{print int($CPU_VAL+0)}" 2>/dev/null || echo 0)
            CLR=$(color_for "$CPU_INT")
            printf "  %-40s %s%7s%s %18s %16s\033[K\n" \
                "${NAME:0:40}" "$CLR" "$CPU_C" "$RST" "$MEM_C" "$NET_C"
        done
        printf '\033[K\n'
    else
        printf "${R}  docker not available — container roster skipped${RST}\033[K\n\033[K\n"
    fi

    printf "${D}  Refresh: ${INTERVAL}s · Ctrl+C to exit${RST}\033[K\n"

    # Clear everything below the cursor so a shorter frame (e.g. container
    # count dropped) doesn't leave orphaned rows from the previous tick.
    printf '\033[J'

    sleep "$INTERVAL"
done
