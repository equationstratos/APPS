#!/usr/bin/env bash
# Install Nexmon_CSI on a Raspberry Pi 3B+/4 (BCM43455c0).
# Run as root on the Pi itself.
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root." >&2
  exit 1
fi

apt-get update
apt-get install -y raspberrypi-kernel-headers git libgmp3-dev gawk qpdf bison \
    flex make automake autoconf libtool tcpdump

cd /opt
[[ -d nexmon ]] || git clone https://github.com/seemoo-lab/nexmon.git
cd nexmon
source setup_env.sh
make

cd /opt
[[ -d nexmon_csi ]] || git clone https://github.com/seemoo-lab/nexmon_csi.git
cd nexmon_csi
make install-firmware

cat <<EOF

Nexmon_CSI installed. To start a capture:

  mcp -C 1 -N 1 -c 36/80 | xxd -r -p | nexutil -Iwlan0 -s500 -b -l34 -v\$(...)
  tcpdump -i wlan0 -nn -s 0 'dst port 5500' -w - | nc -u <host-running-ruview> 5500

Then on the host:

  python -m ruview --source rpi --udp-port 5500 --publish
EOF
