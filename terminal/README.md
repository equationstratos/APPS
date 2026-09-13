# Terminal — un shell façon Ubuntu sur Android

Application Android (Kotlin + Jetpack Compose) qui fournit un terminal complet :
un shell POSIX, un gestionnaire de paquets `apt`, un vrai client SSH, et les
outils réseau habituels.

## Ce qui est réel

| Élément | Comment ça marche |
|---|---|
| Le shell | Les commandes inconnues de l'application sont passées à `/system/bin/sh` (mksh), le vrai shell d'Android. Tubes, redirections, jokers, `&&`, `;`, boucles : c'est mksh qui les exécute. |
| Les commandes Unix | `ls`, `cat`, `grep`, `sed`, `find`, `ps`, `df`, `du`, `tar`, `chmod`, `mount`… viennent de **toybox**, réellement présent dans `/system/bin` sur tout appareil Android. |
| SSH | Client SSH-2 complet (bibliothèque JSch) : shell distant avec pseudo-terminal, exécution de commande, `scp`, `sftp`, `ssh-keygen` (RSA/ECDSA/Ed25519), `known_hosts`, authentification par mot de passe, clavier-interactif ou clé. |
| Réseau | `curl`, `wget`, `dig`, `host`, `nslookup`, `ifconfig`, `netstat`, `nc` : vraies sockets, vraies requêtes HTTP(S), vraies résolutions DNS. `ping` délègue au binaire ICMP du système. |
| Les fichiers | Chemins réels, à la manière de Termux : `$PREFIX` et `$HOME` pointent sur le stockage privé de l'application, le reste du système de fichiers Android reste visible. Aucun chemin n'est simulé. |
| Les paquets à script | `apt install hello` écrit réellement `$PREFIX/bin/hello` sur le disque ; le script est ensuite exécuté par mksh. |

## Ce qui est une reconstitution

Le dépôt `apt` n'est **pas** celui d'Ubuntu et les paquets ne sont pas des `.deb` :
installer une vraie distribution demanderait PRoot et une image système de plusieurs
centaines de mégaoctets. Ici, un paquet est soit un script shell réellement installé,
soit un module intégré à l'application que l'installation rend disponible. La base des
paquets installés, les dépendances, la suppression et les messages suivent en revanche
le comportement d'`apt` (y compris la confirmation quand des dépendances s'ajoutent).

On peut ajouter ses propres dépôts HTTP dans `$PREFIX/etc/apt/sources.list` ;
ils doivent servir un `index.json` au même format que
`app/src/main/assets/repo/index.json`.

## Prise en main

```sh
apt update                      # met à jour les listes
apt list                        # 29 paquets disponibles
apt search ssh
apt install openssh-client      # installe ssh, scp, sftp, ssh-keygen
ssh-keygen -t ed25519           # génère une clé (empreinte + image aléatoire)
ssh utilisateur@serveur         # shell distant interactif
scp notes.txt user@serveur:/tmp/
apt install nano tree htop neofetch
nano notes.txt                  # éditeur plein écran (^O écrire, ^X quitter)
ps -A | grep terminal           # tubes réels via mksh
help                            # aide complète
```

## Paquets fournis

`openssh-client`, `curl`, `wget`, `iputils-ping`, `net-tools`, `dnsutils`,
`netcat-openbsd`, `nano`, `htop`, `tree`, `neofetch`, `figlet`, `cowsay`,
`fortune-mod`, `cmatrix`, `sl`, `zip`, `unzip`, `hello`, `sysinfo`,
plus les paquets de base (`apt`, `dpkg`, `coreutils`, `mksh`, `libc6`, `base-files`)
et les bibliothèques (`libssl3`, `zlib1g`, `libncurses6`).

Une commande non installée affiche le message d'Ubuntu :

```
La commande « ssh » n'a pas été trouvée, mais peut être installée avec :

apt install openssh-client
```

## Interface

- Onglets : plusieurs sessions indépendantes, chacune avec son shell et son historique.
- Rangée de touches : `ESC`, `TAB` (complétion), `^C`, `^D`, flèches, et les caractères
  absents du clavier Android (`|`, `~`, `$`, `*`, `{}`, `[]`…).
- Historique avec les flèches haut/bas, complétion des commandes et des fichiers par `TAB`.
- Couleurs ANSI complètes (16 couleurs, 256 couleurs, RVB), sélection et copie du texte.

## Construction

```sh
cd terminal
./gradlew assembleDebug
```

L'APK est aussi construit automatiquement par GitHub Actions et publié dans `apk/terminal.apk`.

- `minSdk` 26, `targetSdk` 35, Kotlin 2.0, Compose BOM 2024.11
- Dépendance : `com.github.mwiede:jsch` (fork maintenu de JSch, algorithmes modernes)
- Permissions : `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`

## Limites connues

- Pas de pseudo-terminal local : les applications plein écran locales (`vi`, `top`)
  ne se redessinent pas correctement. Elles fonctionnent en revanche **à distance**
  par SSH, où le serveur fournit un vrai pty.
- Android masque `/proc` des autres applications : `htop` et `netstat` ne voient
  que les processus et sockets du terminal.
- Pas d'accès root : les commandes qui l'exigent échouent comme sur un système normal.
