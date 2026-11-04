# TV Guard - bo canh nut nguon (chay bang quyen shell, KHONG bi HyperOS giet khi no "dong tat ca chuong trinh")
# Khi bam nut nguon dieu khien: tat nguon that (reboot -p) ngay. Ghi nhat ky vao /data/local/tmp/tvguard_watch.log
LOG=/data/local/tmp/tvguard_watch.log
F=/data/local/tmp/tvguard_fired
SS=$(pidof system_server)
k=0
rm -f $F
echo "$(date +%T) bat dau canh (system_server=$SS)" > $LOG

fire() {
  if [ -e $F ]; then return; fi
  touch $F
  echo "$(date +%T) TAT NGUON: $1" >> $LOG
  reboot -p
  sleep 40
  rm -f $F
}

# Nguon 1: phim NGUON tu dieu khien (doc truc tiep thiet bi nhap)
getevent -l 2>/dev/null | while read -r l; do
  k=$((k + 1))
  case "$l" in
    *KEY_POWER*DOWN*) fire "phim nguon dieu khien";;
    *POWER*|*SLEEP*|*STANDBY*|*SUSPEND*) echo "$(date +%T) phim: $l" >> $LOG;;
    *) if [ $k -le 30 ]; then echo "$(date +%T) su kien nhap: $l" >> $LOG; fi;;
  esac
done &

# Nguon 3 + 4 (khong phu thuoc phim/nhat ky): hoi 1 giay/lan
#  - he thong bat dau quy trinh tat nguon (sys.shutdown.requested co gia tri)
#  - man hinh chuyen sang trang thai ngu (Asleep) 2 lan lien tiep (bam nut nguon chuyen sang che do cho/tat)
(
  a=0
  while true; do
    sd=$(getprop sys.shutdown.requested)
    if [ -n "$sd" ]; then fire "he thong dang tat nguon ($sd)"; fi
    w=$(dumpsys power 2>/dev/null | grep -m1 -o 'mWakefulness=[A-Za-z]*')
    case "$w" in
      *Asleep*) a=$((a + 1)); if [ $a -ge 2 ]; then fire "man hinh tat ($w)"; fi;;
      *) a=0;;
    esac
    sleep 1
  done
) &

# Nguon 2: he thong bat dau "dong tat ca chuong trinh" = system_server buoc dung hang loat cac app trong vai giay
if [ -n "$SS" ]; then
  n=0; t0=0; mine=0; m=0
  logcat -b system -v brief -T 1 ActivityManager:I '*:S' 2>/dev/null | while read -r l; do
    case "$l" in
      *"Force stopping "*"from pid $SS"*)
        m=$((m + 1))
        if [ $m -le 40 ]; then echo "$(date +%T) dong app: $l" >> $LOG; fi
        now=$(date +%s)
        if [ $((now - t0)) -gt 3 ]; then n=0; mine=0; t0=$now; fi
        n=$((n + 1))
        case "$l" in *"Force stopping com.tvguard.app "*) mine=1;; esac
        if [ $n -ge 4 ]; then
          fire "he thong dong hang loat $n app"
        elif [ $n -ge 2 ] && [ $mine = 1 ]; then
          fire "he thong dong app (co TV Guard) $n app"
        fi
        ;;
    esac
  done
fi
wait
