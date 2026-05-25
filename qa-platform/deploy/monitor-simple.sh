#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# QA Platform — Minimal in-place host monitor
#
# Strips every decoration that was making the in-place refresh look like
# it was "overwriting" previous frames (progress bars, multi-column
# container rosters, variable-width labels). What's left: a tiny grid of
# colour-coded numbers anchored at fixed terminal coordinates via
# `tput cup`. Each line is also terminated with \033[K to wipe any
# leftover characters, so even on a flaky terminal the ghosting can't
# come back.
#
# Refreshes every 0.5s by default. Pass an interval as first arg:
#   bash monitor-simple.sh        # 0.5s
#   bash monitor-simple.sh 1      # 1s
#   bash monitor-simple.sh 0.25   # 4 updates/sec
# Ctrl+C to exit.
# ─────────────────────────────────────────────────────────────────────────────
set -u

INTERVAL="${1:-0.5}"

# ── ANSI ──────────────────────────────────────────────────────────────
R=$'\033[31m'  Y=$'\033[33m'  G=$'\033[32m'
B=$'\033[1m'   D=$'\033[2m'   RST=$'\033[0m'

# Pick a colour for a 0–100 percentage
clr() {
    local p=$1
    [[ $p -ge 80 ]] && { echo "$R"; return; }
    [[ $p -ge 50 ]] && { echo "$Y"; return; }
    echo "$G"
}

# Human-readable byte rate
hr_rate() {
    awk -v n="$1" 'BEGIN {
        if      (n >= 1024*1024*1024) printf "%6.2f GB/s", n/1024/1024/1024;
        else if (n >= 1024*1024)      printf "%6.2f MB/s", n/1024/1024;
        else if (n >= 1024)           printf "%6.0f KB/s", n/1024;
        else                          printf "%6d B/s",   n;
    }'
}

cleanup() { tput cnorm 2>/dev/null; tput cup 20 0 2>/dev/null; printf "%s\n" "$RST"; exit 0; }
trap cleanup INT TERM
tput civis 2>/dev/null
clear

# CPU sampling (delta between iterations)
read_cpu() {
    awk '/^cpu / {
        t=$2+$3+$4+$5+$6+$7+$8+$9; i=$5+$6
        print t, t-i
    }' /proc/stat
}
read -r T0 B0 < <(read_cpu)

# Net sampling
read_net() {
    awk '/^[ ]*[a-z0-9]+:/ && !/lo:/ {
        gsub(":", "", $1); rx+=$2; tx+=$10
    } END { print rx+0, tx+0 }' /proc/net/dev
}
read -r RX0 TX0 < <(read_net)
TS0=$(date +%s.%N)

# ── Loop ──────────────────────────────────────────────────────────────
# Print the static labels once; from then on we just update the values
# in place at fixed coordinates.
tput cup 0 0
printf "${B}━━━ Host Monitor · %s ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RST}\033[K\n" "$(hostname -s)"
printf "\033[K\n"
printf "  ${B}CPU${RST}    \033[K\n"
printf "  ${B}RAM${RST}    \033[K\n"
printf "  ${B}Swap${RST}   \033[K\n"
printf "  ${B}Disk${RST}   \033[K\n"
printf "\033[K\n"
printf "  ${B}Load${RST}   \033[K\n"
printf "  ${B}Net${RST}    \033[K\n"
printf "\033[K\n"
printf "${D}  Refresh: ${INTERVAL}s · Ctrl+C to exit${RST}\033[K\n"

while true; do
    # ── Read all stats ────────────────────────────────────────────
    read -r T1 B1 < <(read_cpu)
    DT=$(( T1 - T0 )); DB=$(( B1 - B0 ))
    CPU=0
    [[ $DT -gt 0 ]] && CPU=$(( DB * 100 / DT ))
    T0=$T1; B0=$B1

    eval "$(awk '
        /^MemTotal:/    {t=$2}
        /^MemAvailable:/{a=$2}
        /^SwapTotal:/   {st=$2}
        /^SwapFree:/    {sf=$2}
        END { printf "MU=%d MT=%d SU=%d ST=%d", t-a, t, st-sf, st }
    ' /proc/meminfo)"
    MEM=$(( MT>0 ? MU*100/MT : 0 ))
    SWAP=$(( ST>0 ? SU*100/ST : 0 ))
    MU_H=$(awk "BEGIN{printf \"%.1fG\", $MU/1024/1024}")
    MT_H=$(awk "BEGIN{printf \"%.1fG\", $MT/1024/1024}")
    if [[ $ST -gt 0 ]]; then
        SU_H=$(awk "BEGIN{printf \"%.0fM\", $SU/1024}")
        ST_H=$(awk "BEGIN{printf \"%.1fG\", $ST/1024/1024}")
    else
        SU_H="-"; ST_H="off"
    fi

    DSK=$(df -h / | awk 'NR==2 {print $3"|"$2"|"$5}')
    DU=${DSK%%|*}; rest=${DSK#*|}; DT_H=${rest%%|*}
    DPC=${rest#*|}; DPC=${DPC%\%}

    LOAD=$(awk '{printf "%.2f / %.2f / %.2f", $1, $2, $3}' /proc/loadavg)

    TS1=$(date +%s.%N)
    read -r RX1 TX1 < <(read_net)
    SPAN=$(awk "BEGIN{print $TS1 - $TS0}")
    RX_RATE=$(awk "BEGIN{ if ($SPAN > 0) printf \"%.0f\", ($RX1 - $RX0)/$SPAN; else print 0 }")
    TX_RATE=$(awk "BEGIN{ if ($SPAN > 0) printf \"%.0f\", ($TX1 - $TX0)/$SPAN; else print 0 }")
    RX0=$RX1; TX0=$TX1; TS0=$TS1

    # ── Paint each line at a fixed row ──────────────────────────────
    # `tput cup ROW COL` is absolute positioning — no chance of drift.
    # Column 9 is where the value starts (past the "  CPU    " label).
    # \033[K wipes everything from the cursor to end-of-line.
    tput cup 2 9; printf "%s%3d%%%s  \033[K"     "$(clr $CPU)"  "$CPU"  "$RST"
    tput cup 3 9; printf "%s%3d%%%s  ${D}%s / %s${RST}\033[K" "$(clr $MEM)"  "$MEM"  "$RST" "$MU_H" "$MT_H"
    tput cup 4 9; printf "%s%3d%%%s  ${D}%s / %s${RST}\033[K" "$(clr $SWAP)" "$SWAP" "$RST" "$SU_H" "$ST_H"
    tput cup 5 9; printf "%s%3d%%%s  ${D}%s / %s${RST}\033[K" "$(clr $DPC)"  "$DPC"  "$RST" "$DU"   "$DT_H"
    tput cup 7 9; printf "${D}%s${RST}\033[K" "$LOAD"
    tput cup 8 9; printf "↓ ${G}%s${RST}   ↑ ${G}%s${RST}\033[K" "$(hr_rate $RX_RATE)" "$(hr_rate $TX_RATE)"

    sleep "$INTERVAL"
done
