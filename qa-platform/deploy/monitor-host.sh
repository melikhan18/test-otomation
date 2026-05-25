#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# QA Platform — Host-only resource monitor (no docker)
#
# Trimmed-down counterpart to monitor.sh: skips the per-container roster
# (which costs ~0.5–1.5s per tick on `docker stats --no-stream`) and instead
# focuses on the host: per-core CPU, RAM, swap, disk, network throughput,
# top processes. The freed-up budget gives a real 0.5s refresh — actually
# 2 updates per second instead of "asked for 0.5, got 1.0 because docker".
#
# Pass refresh interval as first arg (default 0.5s):
#   bash monitor-host.sh            # 2 updates/sec
#   bash monitor-host.sh 0.25       # 4 updates/sec
#   bash monitor-host.sh 1          # gentler on the eye
#
# Zero external dependencies — uses /proc, awk, df, ps. Works on any
# stock Ubuntu/Debian. Ctrl+C to exit; cursor + colour state restored.
# ─────────────────────────────────────────────────────────────────────────────
set -u

INTERVAL="${1:-0.5}"

# ── ANSI ───────────────────────────────────────────────────────────────────
R=$'\033[31m'  Y=$'\033[33m'  G=$'\033[32m'
C=$'\033[36m'  D=$'\033[2m'   B=$'\033[1m'   RST=$'\033[0m'

color_for() {
    local p=$1
    [[ $p -ge 80 ]] && { echo "$R"; return; }
    [[ $p -ge 50 ]] && { echo "$Y"; return; }
    echo "$G"
}

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

# Human-readable byte rate (input: bytes/sec)
hr_rate() {
    awk -v n="$1" 'BEGIN {
        if (n >= 1024*1024*1024)      printf "%.2f GB/s", n/1024/1024/1024;
        else if (n >= 1024*1024)      printf "%.2f MB/s", n/1024/1024;
        else if (n >= 1024)           printf "%.0f KB/s", n/1024;
        else                          printf "%d B/s",    n;
    }'
}

cleanup() { tput cnorm 2>/dev/null; printf "%s\n" "$RST"; exit 0; }
trap cleanup INT TERM
tput civis 2>/dev/null
clear

# ── CPU per-core sampling ──────────────────────────────────────────────────
# Read every `cpuN ...` line, not just the aggregate, so we can render a
# bar per core. Stored in a parallel-indexed array (prev/curr totals).
read_all_cpu() {
    awk '/^cpu[0-9]/ {
        total = $2+$3+$4+$5+$6+$7+$8+$9
        idle  = $5+$6
        print $1, total, total-idle
    }' /proc/stat
}

declare -A CPU_TOT_PREV CPU_BUSY_PREV
while read -r name tot busy; do
    CPU_TOT_PREV[$name]=$tot
    CPU_BUSY_PREV[$name]=$busy
done < <(read_all_cpu)

# ── Network throughput sampling ────────────────────────────────────────────
# Sum every interface except `lo` for a single host-level RX/TX number.
read_net() {
    awk '/^[ ]*[a-z0-9]+:/ && !/lo:/ {
        gsub(":", "", $1)
        rx += $2; tx += $10
    }
    END { print rx+0, tx+0 }' /proc/net/dev
}
read -r NET_RX_PREV NET_TX_PREV < <(read_net)
PREV_TS=$(date +%s.%N)

# ── Main loop ──────────────────────────────────────────────────────────────
while true; do
    printf '\033[H'   # cursor home — repaint in place

    NOW=$(date '+%F %T')
    HOST=$(hostname -s)
    UPT=$(uptime -p 2>/dev/null | sed 's/^up //' || echo "?")
    LOAD=$(awk '{printf "%.2f / %.2f / %.2f", $1, $2, $3}' /proc/loadavg)

    # `\033[K` before every `\n` erases the rest of the line, so trailing
    # leftover characters from a previous (longer) frame don't ghost on
    # screen. Without it, "CPU 12%" → "CPU 3%" overwrites the "1" but
    # leaves the "2%" hanging.
    printf "${B}${C}━━━ Host Monitor · %s ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}\033[K\n" "$HOST"
    printf "  ${D}Time:${RST} %s    ${D}Up:${RST} %s\033[K\n" "$NOW" "$UPT"
    printf "  ${D}Load:${RST} %s  ${D}(1m / 5m / 15m)${RST}\033[K\n\033[K\n" "$LOAD"

    # ── Aggregate CPU + per-core ──────────────────────────────────────
    printf "${B}${C}━━━ CPU ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}\033[K\n"
    declare -A CPU_TOT_NOW CPU_BUSY_NOW
    while read -r name tot busy; do
        CPU_TOT_NOW[$name]=$tot
        CPU_BUSY_NOW[$name]=$busy
    done < <(read_all_cpu)

    # First print the aggregate
    AGG=$(awk '/^cpu / {
        total=$2+$3+$4+$5+$6+$7+$8+$9
        idle=$5+$6
        print total, total-idle
    }' /proc/stat)
    read -r AGG_TOT AGG_BUSY <<< "$AGG"
    AGG_TOT_PREV=${CPU_TOT_PREV[cpu]:-0}
    AGG_BUSY_PREV=${CPU_BUSY_PREV[cpu]:-0}
    DT=$(( AGG_TOT - AGG_TOT_PREV ))
    DB=$(( AGG_BUSY - AGG_BUSY_PREV ))
    AGG_PCT=0
    [[ $DT -gt 0 ]] && AGG_PCT=$(( DB * 100 / DT ))
    CPU_TOT_PREV[cpu]=$AGG_TOT
    CPU_BUSY_PREV[cpu]=$AGG_BUSY

    printf "  ${B}all${RST}  "; bar $AGG_PCT 50
    printf "  %s%3d%%%s\033[K\n" "$(color_for $AGG_PCT)" "$AGG_PCT" "$RST"

    # Per-core bars (sorted by index)
    for core in $(echo "${!CPU_TOT_NOW[@]}" | tr ' ' '\n' | sort -V); do
        tot=${CPU_TOT_NOW[$core]}
        busy=${CPU_BUSY_NOW[$core]}
        tot_prev=${CPU_TOT_PREV[$core]:-0}
        busy_prev=${CPU_BUSY_PREV[$core]:-0}
        dt=$(( tot - tot_prev ))
        db=$(( busy - busy_prev ))
        pct=0
        [[ $dt -gt 0 ]] && pct=$(( db * 100 / dt ))
        CPU_TOT_PREV[$core]=$tot
        CPU_BUSY_PREV[$core]=$busy
        printf "  ${D}%-4s${RST} "; bar $pct 50
        printf "  %s%3d%%%s\033[K\n" "$(color_for $pct)" "$pct" "$RST"
    done
    printf '\033[K\n'

    # ── Memory + Swap ──────────────────────────────────────────────────
    eval "$(awk '
        /^MemTotal:/    {t=$2}
        /^MemAvailable:/{a=$2}
        /^MemFree:/     {f=$2}
        /^Buffers:/     {b=$2}
        /^Cached:/      {c=$2}
        /^SwapTotal:/   {st=$2}
        /^SwapFree:/    {sf=$2}
        END {
            printf "MU=%d MT=%d MFREE=%d MCACHE=%d SU=%d ST=%d",
                t-a, t, f, b+c, st-sf, st
        }
    ' /proc/meminfo)"
    MEM=$(( MT > 0 ? MU * 100 / MT : 0 ))
    SWAP=$(( ST > 0 ? SU * 100 / ST : 0 ))
    MU_H=$(awk    "BEGIN{printf \"%.2fG\", $MU/1024/1024}")
    MT_H=$(awk    "BEGIN{printf \"%.2fG\", $MT/1024/1024}")
    MFREE_H=$(awk "BEGIN{printf \"%.2fG\", $MFREE/1024/1024}")
    MCACHE_H=$(awk "BEGIN{printf \"%.2fG\", $MCACHE/1024/1024}")
    if [[ $ST -gt 0 ]]; then
        SU_H=$(awk "BEGIN{printf \"%.0fM\", $SU/1024}")
        ST_H=$(awk "BEGIN{printf \"%.2fG\", $ST/1024/1024}")
    else
        SU_H="–"; ST_H="off"
    fi

    printf "${B}${C}━━━ Memory ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}\033[K\n"
    printf "  ${B}RAM${RST}  "; bar $MEM 50
    printf "  %s%3d%%%s  ${D}%s / %s${RST}\033[K\n" "$(color_for $MEM)" "$MEM" "$RST" "$MU_H" "$MT_H"
    printf "  ${D}     free: %s   cached/buffers: %s${RST}\033[K\n" "$MFREE_H" "$MCACHE_H"
    printf "  ${B}Swap${RST} "; bar $SWAP 50
    printf "  %s%3d%%%s  ${D}%s / %s${RST}\033[K\n\033[K\n" "$(color_for $SWAP)" "$SWAP" "$RST" "$SU_H" "$ST_H"

    # ── Disk + Network ─────────────────────────────────────────────────
    DSK=$(df -h / | awk 'NR==2 {print $3 "|" $2 "|" $5}')
    DU=${DSK%%|*}; rest=${DSK#*|}
    DT_H=${rest%%|*}; DPCS=${rest#*|}
    DPC=${DPCS%\%}

    NOW_TS=$(date +%s.%N)
    read -r NET_RX NET_TX < <(read_net)
    SPAN=$(awk "BEGIN{print $NOW_TS - $PREV_TS}")
    RX_RATE=$(awk "BEGIN{ if ($SPAN > 0) printf \"%.0f\", ($NET_RX - $NET_RX_PREV) / $SPAN; else print 0 }")
    TX_RATE=$(awk "BEGIN{ if ($SPAN > 0) printf \"%.0f\", ($NET_TX - $NET_TX_PREV) / $SPAN; else print 0 }")
    NET_RX_PREV=$NET_RX; NET_TX_PREV=$NET_TX; PREV_TS=$NOW_TS

    printf "${B}${C}━━━ Disk + Network ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}\033[K\n"
    printf "  ${B}/${RST}    "; bar $DPC 50
    printf "  %s%3d%%%s  ${D}%s / %s${RST}\033[K\n" "$(color_for $DPC)" "$DPC" "$RST" "$DU" "$DT_H"
    printf "  ${B}Net${RST}  ↓ %s${D} rx${RST}   ↑ %s${D} tx${RST}\033[K\n\033[K\n" "$(hr_rate $RX_RATE)" "$(hr_rate $TX_RATE)"

    # ── Top processes ──────────────────────────────────────────────────
    printf "${B}${C}━━━ Top Processes ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}\033[K\n"
    printf "  ${B}%-6s %-25s %7s %7s${RST}      ${D}(by CPU)${RST}\033[K\n" "PID" "COMMAND" "CPU%" "MEM%"
    ps -eo pid,comm,%cpu,%mem --sort=-%cpu --no-headers 2>/dev/null \
        | head -5 \
        | awk '{ printf "  %-6s %-25.25s %6.1f%% %6.1f%%\033[K\n", $1, $2, $3, $4 }'
    printf '\033[K\n'
    printf "  ${B}%-6s %-25s %7s %7s${RST}      ${D}(by MEM)${RST}\033[K\n" "PID" "COMMAND" "CPU%" "MEM%"
    ps -eo pid,comm,%cpu,%mem --sort=-%mem --no-headers 2>/dev/null \
        | head -5 \
        | awk '{ printf "  %-6s %-25.25s %6.1f%% %6.1f%%\033[K\n", $1, $2, $3, $4 }'

    printf "\033[K\n${D}  Refresh: ${INTERVAL}s · Ctrl+C to exit${RST}\033[K\n"

    # Clear leftover lines from any previous (taller) frame
    printf '\033[J'

    sleep "$INTERVAL"
done
