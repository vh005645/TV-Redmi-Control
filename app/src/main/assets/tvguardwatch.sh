# ver 3
# TV Guard - bo canh (chay bang quyen shell, KHONG bi HyperOS giet khi no "dong tat ca chuong trinh")
# Nhat ky: /data/local/tmp/tvguard_watch.log
LOG=/data/local/tmp/tvguard_watch.log
F=/data/local/tmp/tvguard_fired
K=/data/local/tmp/tvguard_keys
G=/data/local/tmp/tvguard_grace
APP=com.tvguard.app
SS=$(pidof system_server)
ST=$(date +%s)
k=0; bk=0; bt=0
rm -f $F $K
echo "$(date +%T) bat dau canh v3 (system_server=$SS)" > $LOG

# Mo lai app: go trang thai "stopped" sau khi bi buoc dung + khoi dong lai tien trinh (app tu bat lai Tro nang, ket noi Firebase)
relaunch() {
  out=$(am start -n $APP/.MainActivity --ez minimize true --activity-no-animation 2>&1 | tr '\n' ' ')
  echo "$(date +%T) mo lai app: $out" >> $LOG
}

# Tat nguon that. Mo lai app TRUOC de lan bat sau BOOT_COMPLETED toi duoc app (app bi buoc dung thi khong nhan BOOT_COMPLETED).
# Lenh reboot chay trong tien trinh tach roi de khong bi mat neu bo canh bi khoi dong lai.
fire() {
  if [ -e $F ]; then return; fi
  touch $F
  echo "$(date +%T) TAT NGUON: $1" >> $LOG
  nohup sh -c 'sleep 3; reboot -p' >/dev/null 2>&1 </dev/null &
  relaunch
  sleep 40
  rm -f $F
}

# Nguon 1: phim tu dieu khien (doc thiet bi nhap). Ghi lai cac phim de biet nut nguon cua dieu khien hien ra the nao.
getevent -l 2>/dev/null | while read -r l; do
  case "$l" in
    *KEY_POWER*DOWN*) fire "phim nguon dieu khien";;
    *KEY_BACK*DOWN*|*KEY_HOME*DOWN*|*KEY_ESC*DOWN*)
      now=$(date +%s)
      if [ $((now - bt)) -gt 6 ]; then bk=0; fi
      bk=$((bk + 1)); bt=$now
      echo "$bk $bt" > $K
      k=$((k + 1)); if [ $k -le 80 ]; then echo "$(date +%T) phim: $l" >> $LOG; fi;;
    *EV_KEY*DOWN*) k=$((k + 1)); if [ $k -le 80 ]; then echo "$(date +%T) phim: $l" >> $LOG; fi;;
    *POWER*|*SLEEP*|*STANDBY*|*SUSPEND*) echo "$(date +%T) phim: $l" >> $LOG;;
  esac
done &

# Nguon 2: he thong buoc dung TV Guard (system_server) = HyperOS "dong tat ca chuong trinh" khi bam nut nguon (che do Shutdown)
#   - neu ngay truoc do co bam Back/Home lien tuc (>=2 lan) -> tre dang pha khoa: chi MO LAI app, khong tat TV
#   - neu vua cap quyen bang ADB (30 giay) -> chi mo lai app
#   - con lai -> TAT NGUON THAT
if [ -n "$SS" ]; then
  logcat -b system -v brief -T 1 ActivityManager:I '*:S' 2>/dev/null | while read -r l; do
    case "$l" in
      *"Force stopping com.tvguard.app "*"from pid $SS"*)
        now=$(date +%s)
        if [ $((now - ST)) -lt 3 ]; then continue; fi
        echo "$(date +%T) dong app: $l" >> $LOG
        spam=0
        if [ -r $K ]; then
          set -- $(cat $K)
          if [ -n "$2" ] && [ $((now - $2)) -le 8 ] && [ "$1" -ge 2 ]; then spam=1; fi
        fi
        grace=0
        if [ -r $G ]; then
          g=$(cat $G)
          if [ -n "$g" ] && [ $((now - g)) -le 30 ]; then grace=1; fi
        fi
        if [ $spam = 1 ]; then
          echo "$(date +%T) do bam Back/Home lien tuc -> chi mo lai app" >> $LOG
          relaunch
        elif [ $grace = 1 ]; then
          echo "$(date +%T) vua cap quyen -> chi mo lai app" >> $LOG
          relaunch
        else
          fire "he thong buoc dung TV Guard"
        fi;;
    esac
  done
fi
wait
